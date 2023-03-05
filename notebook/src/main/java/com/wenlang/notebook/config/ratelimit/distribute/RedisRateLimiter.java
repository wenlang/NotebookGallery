package com.wenlang.notebook.config.ratelimit.distribute;

import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.jedis.JedisClusterConnection;
import org.springframework.data.redis.connection.jedis.JedisConnection;
import org.springframework.data.redis.core.RedisConnectionUtils;
import org.springframework.data.redis.core.RedisTemplate;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisCluster;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;



public class RedisRateLimiter {
    private RedisTemplate template;
    private TimeUnit timeUnit;
    private int permitsPerUnit;

    //获取当前时间的微秒数
    private static final String CURRENT_MICROSECOND_SCRIPT = "local a=redis.call('TIME') ;return a[1]*1000000+a[2]";

    //基于时间段的伪平滑限流（针对秒）
    private static final String LUA_SECOND_SCRIPT = " local current; "
            + " current = redis.call('incr',KEYS[1]); "
            + " if tonumber(current) == 1 then "
            + " 	redis.call('expire',KEYS[1],ARGV[1]); "
            + "     return 1; "
            + " else"
            + " 	if tonumber(current) <= tonumber(ARGV[2]) then "
            + "     	return 1; "
            + "		else "
            + "			return -1; "
            + "     end "
            + " end ";

    private static final String LUA_PERIOD_SCRIPT = " local currentSectionCount;"
            + " local previosSectionCount;"
            + " local totalCountInPeriod;"
            + " currentSectionCount = redis.call('zcount', KEYS[2], '-inf', '+inf');"
            + " previosSectionCount = redis.call('zcount', KEYS[1], ARGV[3], '+inf');"
            + " totalCountInPeriod = tonumber(currentSectionCount)+tonumber(previosSectionCount);"
            + " if totalCountInPeriod < tonumber(ARGV[5]) then "
            + " 	redis.call('zadd',KEYS[2],ARGV[1],ARGV[2]);"
            + "		if tonumber(currentSectionCount) == 0 then "
            + "			redis.call('expire',KEYS[2],ARGV[4]); "
            + "		end "
            + "     return 1"
            + "	else "
            + " 	return -1"
            + " end ";

    private static final int PERIOD_SECOND_TTL = 3;
    private static final int PERIOD_MINUTE_TTL = 2 * 60 + 10;
    private static final int PERIOD_HOUR_TTL = 2 * 3600 + 10;
    private static final int PERIOD_DAY_TTL = 2 * 3600 * 24 + 10;

    private static final int MICROSECONDS_IN_MINUTE = 60 * 1000000;
    private static final int MICROSECONDS_IN_HOUR = 3600 * 1000000;
    private static final int MICROSECONDS_IN_DAY = 24 * 3600 * 1000000;

    public RedisRateLimiter(RedisTemplate template, TimeUnit timeUnit, int permitsPerUnit) {
        this.template = template;
        this.timeUnit = timeUnit;
        this.permitsPerUnit = permitsPerUnit;
    }


    public TimeUnit getTimeUnit() {
        return timeUnit;
    }

    public int getPermitsPerSecond() {
        return permitsPerUnit;
    }


    private String getKeyNameForSecond(String currentSecond, String keyPrefix) {
        String keyName = keyPrefix + ":" + currentSecond;
        return keyName;
    }

    public Long getCurrentMicrosecond(JedisCluster jedisCluster, String key) {
        return (Long) jedisCluster.eval(CURRENT_MICROSECOND_SCRIPT, key);
    }

    public boolean acquire(String keyPrefix) {
        boolean rtv = false;
        if (template != null) {
            RedisConnection conn = null;
            try {

                conn = RedisConnectionUtils.getConnection(template.getConnectionFactory());
                if (conn instanceof JedisConnection) {
                    Jedis jedis = (Jedis) conn.getNativeConnection();
                    if (timeUnit == TimeUnit.SECONDS) {
                        String keyName = getKeyNameForSecond(jedis.time().get(0), keyPrefix);
                        List<String> keys = new ArrayList<String>();
                        keys.add(keyName);
                        List<String> argvs = new ArrayList<String>();
                        argvs.add(String.valueOf(getExpire()));
                        argvs.add(String.valueOf(permitsPerUnit / 2));

                        Long val = (Long) jedis.eval(LUA_SECOND_SCRIPT, keys, argvs);
                        rtv = (val > 0);
                    } else if (timeUnit == TimeUnit.MINUTES || timeUnit == TimeUnit.HOURS || timeUnit == TimeUnit.DAYS) {
                        rtv = doPeriod(jedis, keyPrefix);
                    }
                } else if (conn instanceof JedisClusterConnection) {
                    JedisCluster jedisCluster = (JedisCluster) conn.getNativeConnection();
                    //当前时间点微秒
                    long microSecondsElapseInCurrentSecond = getCurrentMicrosecond(jedisCluster, keyPrefix);
                    //当前时间点秒
                    long currentSecond = microSecondsElapseInCurrentSecond / 1000000;
                    if (timeUnit == TimeUnit.SECONDS) {
                        String keyName = getKeyNameForSecond(currentSecond + "", keyPrefix);
                        List<String> keys = new ArrayList<String>();
                        keys.add(keyName);
                        List<String> argvs = new ArrayList<String>();
                        argvs.add(String.valueOf(getExpire()));
                        argvs.add(String.valueOf(permitsPerUnit / 2));

                        Long val = (Long) jedisCluster.eval(LUA_SECOND_SCRIPT, keys, argvs);
                        rtv = (val > 0);
                    } else if (timeUnit == TimeUnit.MINUTES || timeUnit == TimeUnit.HOURS || timeUnit == TimeUnit.DAYS) {
                        rtv = doPeriod(currentSecond, microSecondsElapseInCurrentSecond, jedisCluster, keyPrefix);
                    }
                } else {
                    //目前不支持redis-cluster集群模式
                    throw new RuntimeException("不支持的redis集群模式");
                }

            } finally {
                if (conn != null) {
                    RedisConnectionUtils.releaseConnection(conn, template.getConnectionFactory());
                }
            }
        }
        return rtv;
    }

    /**
     * @param jedis
     * @param keyPrefix
     * @return redis time命令会返回一个包含两个字符串的列表： 第一个字符串是当前时间(以 UNIX 时间戳格式表示，是从1970年1月1日（UTC/GMT的午夜）开始所经过的秒数，不考虑闰秒)，
     * 而第二个字符串是当前这一秒钟已经逝去的微秒数。
     * <p>
     * <p>
     * 算法说明：zadd(key,score,member)，有序集合，元素不能重复
     * 1、key为时间点，如分钟、小时、天等
     * 2、score为当前时间（精确到微秒），参考getRedisTime（）方法
     * 3、member为score的字符串形式。
     * 需要注意的是：如果同一个时间点有多个请求进来，则score可能相同，即member可能相同，造成漏记？
     * 不用担心，redis是单线程执行的，每一个jedis.time()调用耗时应该会超过1微秒，因此我们可以认为每次jedis.time()返回的时间都是唯一且递增的。
     */
    private boolean doPeriod(Jedis jedis, String keyPrefix) {
        List<String> times = jedis.time();
        //currentSecond
        long currentSecond = Long.parseLong(times.get(0));
        //microSecondsElapseInCurrentSecond
        long microSecondsElapseInCurrentSecond = Long.parseLong(times.get(1));
        //当前时间所在的时间序号和上一个时间序号
        String[] keyNames = getKeyNames(currentSecond, keyPrefix);
        //当前时间点，微秒
        long currentTimeInMicroSecond = getRedisTime(currentSecond, microSecondsElapseInCurrentSecond);
        //当前时间点减去一个时间单位
        String previousSectionBeginScore = String.valueOf((currentTimeInMicroSecond - getPeriodMicrosecond()));
        String expires = String.valueOf(getExpire());
        String currentTimeInMicroSecondStr = String.valueOf(currentTimeInMicroSecond);
        List<String> keys = new ArrayList<String>();
        keys.add(keyNames[0]);
        keys.add(keyNames[1]);
        List<String> argvs = new ArrayList<String>();
        argvs.add(currentTimeInMicroSecondStr);
        argvs.add(currentTimeInMicroSecondStr);
        argvs.add(previousSectionBeginScore);
        argvs.add(expires);
        argvs.add(String.valueOf(permitsPerUnit));
        Long val = (Long) jedis.eval(LUA_PERIOD_SCRIPT, keys, argvs);
        return (val > 0);
    }

    private boolean doPeriod(long currentSecond, long microSecondsElapseInCurrentSecond, JedisCluster jedisCluster, String keyPrefix) {
        //当前时间所在的时间序号和上一个时间序号
        String[] keyNames = getKeyNames(currentSecond, keyPrefix);
        //当前时间点，微秒
        long currentTimeInMicroSecond = getRedisTime(currentSecond, microSecondsElapseInCurrentSecond);
        //当前时间点减去一个时间单位
        String previousSectionBeginScore = String.valueOf((currentTimeInMicroSecond - getPeriodMicrosecond()));
        String expires = String.valueOf(getExpire());
        String currentTimeInMicroSecondStr = String.valueOf(currentTimeInMicroSecond);
        List<String> keys = new ArrayList<String>();
        keys.add(keyNames[0]);
        keys.add(keyNames[1]);
        List<String> argvs = new ArrayList<String>();
        argvs.add(currentTimeInMicroSecondStr);
        argvs.add(currentTimeInMicroSecondStr);
        argvs.add(previousSectionBeginScore);
        argvs.add(expires);
        argvs.add(String.valueOf(permitsPerUnit));
        Long val = (Long) jedisCluster.eval(LUA_PERIOD_SCRIPT, keys, argvs);
        return (val > 0);
    }


    /**
     * 因为redis访问实际上是单线程的，而且jedis.time()方法返回的时间精度为微秒级，每一个jedis.time()调用耗时应该会超过1微秒，因此我们可以认为每次jedis.time()返回的时间都是唯一且递增的
     * <p>
     * redis time命令会返回一个包含两个字符串的列表： 第一个字符串是当前时间(以 UNIX 时间戳格式表示，是从1970年1月1日（UTC/GMT的午夜）开始所经过的秒数，不考虑闰秒)，
     * 而第二个字符串是当前这一秒钟已经逝去的微秒数。
     */
    private long getRedisTime(Long currentSecond, Long microSecondsElapseInCurrentSecond) {
        //currentTimeInMicroSecond
        return currentSecond * 1000000 + microSecondsElapseInCurrentSecond;
    }

    private String[] getKeyNames(Long time, String keyPrefix) {
        String[] keyNames = null;
        if (timeUnit == TimeUnit.MINUTES) {
            long index = time / 60;
            String keyName1 = keyPrefix + ":" + (index - 1);
            String keyName2 = keyPrefix + ":" + index;
            keyNames = new String[]{keyName1, keyName2};
        } else if (timeUnit == TimeUnit.HOURS) {
            long index = time / 3600;
            String keyName1 = keyPrefix + ":" + (index - 1);
            String keyName2 = keyPrefix + ":" + index;
            keyNames = new String[]{keyName1, keyName2};
        } else if (timeUnit == TimeUnit.DAYS) {
            long index = time / (3600 * 24);
            String keyName1 = keyPrefix + ":" + (index - 1);
            String keyName2 = keyPrefix + ":" + index;
            keyNames = new String[]{keyName1, keyName2};
        } else {
            throw new IllegalArgumentException("Don't support this TimeUnit: " + timeUnit);
        }
        return keyNames;
    }

    private int getExpire() {
        int expire = 0;
        if (timeUnit == TimeUnit.SECONDS) {
            expire = PERIOD_SECOND_TTL;
        } else if (timeUnit == TimeUnit.MINUTES) {
            expire = PERIOD_MINUTE_TTL;
        } else if (timeUnit == TimeUnit.HOURS) {
            expire = PERIOD_HOUR_TTL;
        } else if (timeUnit == TimeUnit.DAYS) {
            expire = PERIOD_DAY_TTL;
        } else {
            throw new IllegalArgumentException("Don't support this TimeUnit: " + timeUnit);
        }
        return expire;
    }

    private int getPeriodMicrosecond() {
        if (timeUnit == TimeUnit.MINUTES) {
            return MICROSECONDS_IN_MINUTE;
        } else if (timeUnit == TimeUnit.HOURS) {
            return MICROSECONDS_IN_HOUR;
        } else if (timeUnit == TimeUnit.DAYS) {
            return MICROSECONDS_IN_DAY;
        } else {
            throw new IllegalArgumentException("Don't support this TimeUnit: " + timeUnit);
        }
    }


    public RedisTemplate getTemplate() {
        return template;
    }

    public void setTemplate(RedisTemplate template) {
        this.template = template;
    }
}

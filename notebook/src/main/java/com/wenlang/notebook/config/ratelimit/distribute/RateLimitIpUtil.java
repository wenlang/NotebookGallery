package com.wenlang.notebook.config.ratelimit.distribute;


import org.springframework.util.StringUtils;

import javax.servlet.http.HttpServletRequest;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RateLimitIpUtil {
    /**
     * 获取用户真实IP地址，如果通过了多级反向代理的话，X-Forwarded-For的值并不止一个，而是一串IP值，
     * 取X-Forwarded-For中第一个非unknown的有效IP字符串。
     * 如：X-Forwarded-For：192.168.1.110, 192.168.1.120, 192.168.1.130,
     * 192.168.1.100
     * <p>
     * 用户真实IP为： 192.168.1.110
     *
     * @param request
     * @return
     */
    public static String getIpAddress(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.length() == 0 || "unknown".equalsIgnoreCase(ip)) {
            if (ip == null || ip.length() == 0 || "unknown".equalsIgnoreCase(ip)) {
                ip = request.getHeader("X-Real-IP");
            }
            if (ip == null || ip.length() == 0 || "unknown".equalsIgnoreCase(ip)) {
                ip = request.getHeader("Proxy-Client-IP");
            }
            if (ip == null || ip.length() == 0 || "unknown".equalsIgnoreCase(ip)) {
                ip = request.getHeader("WL-Proxy-Client-IP");
            }
            if (ip == null || ip.length() == 0 || "unknown".equalsIgnoreCase(ip)) {
                ip = request.getHeader("HTTP_CLIENT_IP");
            }
            if (ip == null || ip.length() == 0 || "unknown".equalsIgnoreCase(ip)) {
                ip = request.getHeader("HTTP_X_FORWARDED_FOR");
            }
            if (ip == null || ip.length() == 0 || "unknown".equalsIgnoreCase(ip)) {
                ip = request.getRemoteAddr();
            }
        } else if (ip.length() > 15) {
            String[] ips = ip.split(",");
            for (int index = 0; index < ips.length; index++) {
                String strIp = (String) ips[index];
                if (!("unknown".equalsIgnoreCase(strIp))) {
                    ip = strIp;
                    return ip;
                }
            }
        }
        return ip;
    }

    /**
     * 通过originalIP 校验ip是否有效	<br>
     * 正确originalIP 可能的格式，如：<br>
     * 10.2.0.1  <br>
     * 10.2.0.* <br>
     * 10.2.*.*<br>
     * 10.*.*.*<br>
     * *.*.*.*<br>
     * ip 的格式一定为10.2.0.2
     */
    public static Boolean validIp(String originalIP, String ip) {
        if (!StringUtils.hasText(originalIP)) {
            return true;
        }
        originalIP = originalIP.trim();
        if (!StringUtils.hasText(ip)) {
            return false;
        }
        ip = ip.trim();

        String ipReg = "^((1\\d{2}|2[0-4]\\d|25[0-5]|[1-9]\\d|[1-9])|\\*)\\."
                + "((1\\d{2}|2[0-4]\\d|25[0-5]|[1-9]\\d|\\d)|\\*)\\."
                + "((1\\d{2}|2[0-4]\\d|25[0-5]|[1-9]\\d|\\d)|\\*)\\."
                + "((1\\d{2}|2[0-4]\\d|25[0-5]|[1-9]\\d|\\d)|\\*)$";
        /*校验ip的格式*/
        Pattern pattern = Pattern.compile(ipReg);
        Matcher ipMatcher = pattern.matcher(ip);
        if (!ipMatcher.matches()) {
            return false;
        }
        /*校验originalIP 的格式*/
        Pattern oriPattern = Pattern.compile(ipReg);
        Matcher oriMatcher = oriPattern.matcher(originalIP);
        if (!oriMatcher.matches()) {
            return false;
        }

        /*originalIP与ip 相同*/
        if (originalIP.equals(ip)) {
            return true;
        }

        /*校验ip是否处在originalIP段内*/
        String[] oriIpArr = originalIP.split("\\.");
        String[] ipArr = ip.split("\\.");
        Boolean hasStar = false;
        String star = "*";
        for (int i = 0; i < oriIpArr.length; i++) {
            String oriIp = oriIpArr[i];
            boolean flag = oriIp.equals(star);
            if (flag) {
                hasStar = flag;
            }
            if (hasStar && !flag) {
                return false;
            }
            if (!ipArr[i].equals(oriIp) && !oriIp.equals(star)) {
                return false;
            }
        }
        return true;
    }


}

package com.wenlang.notebook.utils;


/**
 * 描述：检测环境util
 *
 */

public class EnvUtil {

    private static EnvType type = EnvType.TEST;

    public static boolean isDev() {
        return type == EnvType.DEV;
    }

    public static boolean isTest() {
        return type == EnvType.TEST;
    }

    public static boolean isProd() {
        return type == EnvType.PROD;
    }


    /**
     * 设置加载的活跃的profile文件
     */
    public static void setLoadActiveProfiles(String[] activeProfiles) {
        if (activeProfiles == null || activeProfiles.length == 0) {
            return;
        }

        for (String profile : activeProfiles) {
            if (profile.contains("dev")) {
                type = EnvType.DEV;
            } else if (profile.contains("test")) {
                type = EnvType.TEST;
            } else if (profile.contains("prodrel") || profile.contains("prod")) {
                type = EnvType.PROD;
            }
        }
    }


    public enum EnvType {
        DEV("dev"),
        TEST("test"),
        PROD("prod");

        private String str;

        private EnvType(String str) {
            this.str = str;
        }

        public String getStr() {
            return str;
        }

        public static EnvType getByStr(String str) {
            for (EnvType type : values()) {
                if (type.str.equalsIgnoreCase(str)) {
                    return type;
                }
            }

            return null;
        }
    }


}

package com.example.atw.utils;

/**
 * 机器码归一化工具。
 * <p>
 * 机器码由前端（客户机）采集并计算后上报，服务端只做归一化存储与比较，
 * 不再在服务端采集本机硬件信息。
 */
public final class MachineCodeUtil {

    private MachineCodeUtil() {
    }

    /**
     * 归一化机器码：去掉分隔符/空格等非字母数字字符并转大写。
     */
    public static String normalize(String machineCode) {
        if (machineCode == null) {
            return "";
        }
        return machineCode.replaceAll("[^0-9a-zA-Z]", "").toUpperCase();
    }

    /**
     * 便于展示：每 4 位一组，用 '-' 分隔。
     */
    public static String format(String machineCode) {
        String code = normalize(machineCode);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < code.length(); i += 4) {
            if (i > 0) {
                sb.append('-');
            }
            sb.append(code, i, Math.min(i + 4, code.length()));
        }
        return sb.toString();
    }
}

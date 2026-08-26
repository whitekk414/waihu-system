package com.company.outbound.dialog;

final class GuidedAnswerClassifier {
    GuidedIntent identity(String text) {
        String value = normalize(text);
        if (containsAny(value, "不是本人", "不是我", "找错人", "他不在")) return GuidedIntent.DENIED;
        if (containsAny(value, "是本人", "我是", "是的", "本人")) return GuidedIntent.CONFIRMED;
        return GuidedIntent.UNCLEAR;
    }

    GuidedIntent visitConsent(String text) {
        String value = normalize(text);
        if (containsAny(value, "不可以", "不配合", "不同意", "拒绝", "不接受")) return GuidedIntent.DENIED;
        if (containsAny(value, "可以", "配合", "同意", "接受", "没问题")) return GuidedIntent.CONFIRMED;
        return GuidedIntent.UNCLEAR;
    }

    GuidedIntent contactTime(String text) {
        String value = normalize(text);
        if (value.length() < 2) return GuidedIntent.UNCLEAR;
        return containsAny(value, "周", "星期", "今天", "明天", "后天", "上午", "中午", "下午", "晚上", "点", "号", "随时", "工作日")
            ? GuidedIntent.CONFIRMED : GuidedIntent.UNCLEAR;
    }

    private static String normalize(String text) {
        return text == null ? "" : text.replaceAll("[\\s，。！？,.!?]", "");
    }

    private static boolean containsAny(String text, String... values) {
        for (String value : values) if (text.contains(value)) return true;
        return false;
    }
}

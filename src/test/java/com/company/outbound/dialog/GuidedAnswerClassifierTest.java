package com.company.outbound.dialog;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GuidedAnswerClassifierTest {
    private final GuidedAnswerClassifier classifier = new GuidedAnswerClassifier();

    @Test
    void classifiesIdentityAnswers() {
        assertThat(classifier.identity("是的，我是本人")).isEqualTo(GuidedIntent.CONFIRMED);
        assertThat(classifier.identity("不是本人，你找错人了")).isEqualTo(GuidedIntent.DENIED);
        assertThat(classifier.identity("你是谁")).isEqualTo(GuidedIntent.UNCLEAR);
    }

    @Test
    void classifiesVisitConsentAnswers() {
        assertThat(classifier.visitConsent("可以，我配合")).isEqualTo(GuidedIntent.CONFIRMED);
        assertThat(classifier.visitConsent("不可以，我不同意")).isEqualTo(GuidedIntent.DENIED);
        assertThat(classifier.visitConsent("你先说清楚")).isEqualTo(GuidedIntent.UNCLEAR);
    }

    @Test
    void acceptsMeaningfulContactTimeAndRejectsNoise() {
        assertThat(classifier.contactTime("周五下午三点")).isEqualTo(GuidedIntent.CONFIRMED);
        assertThat(classifier.contactTime("嗯")).isEqualTo(GuidedIntent.UNCLEAR);
    }
}

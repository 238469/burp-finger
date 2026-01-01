package com.finger.burp.engine;

import com.finger.burp.model.Fingerprint;
import com.finger.burp.model.Rule;

public class MatchResult {
    private final Fingerprint fingerprint;
    private final Rule matchedRule;

    public MatchResult(Fingerprint fingerprint, Rule matchedRule) {
        this.fingerprint = fingerprint;
        this.matchedRule = matchedRule;
    }

    public Fingerprint getFingerprint() {
        return fingerprint;
    }

    public Rule getMatchedRule() {
        return matchedRule;
    }
}

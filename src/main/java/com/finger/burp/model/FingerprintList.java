package com.finger.burp.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class FingerprintList {
    private List<Fingerprint> fingerprints;

    public List<Fingerprint> getFingerprints() {
        return fingerprints;
    }

    public void setFingerprints(List<Fingerprint> fingerprints) {
        this.fingerprints = fingerprints;
    }
}

package com.runlog.capture;

import com.runlog.data.CandidateConfig;

import java.util.ArrayList;
import java.util.List;

public class CaptureParseResult {
    public CandidateConfig best;
    public final List<CandidateConfig> candidates = new ArrayList<>();
}

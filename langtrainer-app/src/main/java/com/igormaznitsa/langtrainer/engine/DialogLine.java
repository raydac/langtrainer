package com.igormaznitsa.langtrainer.engine;

import com.fasterxml.jackson.annotation.JsonProperty;

public record DialogLine(
    @JsonProperty("A") String a,
    @JsonProperty("B") String b) {
}

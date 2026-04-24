package com.igormaznitsa.langtrainer.engine;

import com.google.gson.annotations.SerializedName;

public record DialogLine(
    @SerializedName("A") String a,
    @SerializedName("B") String b) {
}

package com.igormaznitsa.langtrainer.engine;

import static org.apache.commons.lang3.StringUtils.isBlank;

import com.fasterxml.jackson.annotation.JsonProperty;

public record DialogLine(
    @JsonProperty("A") String a,
    @JsonProperty("B") String b,
    @JsonProperty("image") String image) {

  public DialogLine {
    image = isBlank(image) ? null : image.strip();
  }

  public DialogLine(final String a, final String b) {
    this(a, b, null);
  }

  public boolean hasImage() {
    return !isBlank(this.image);
  }
}

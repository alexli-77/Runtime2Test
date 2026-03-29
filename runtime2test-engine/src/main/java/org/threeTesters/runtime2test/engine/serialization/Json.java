package org.threeTesters.runtime2test.engine.serialization;

import com.fasterxml.jackson.core.util.DefaultIndenter;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.io.IOException;

public class Json {

  private final ObjectMapper objectMapper;

  public Json() {
    this.objectMapper = new ObjectMapper();
    objectMapper.registerModule(new JavaTimeModule());
    objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
  }

  public <T> T fromJson(String input, Class<T> clazz) throws IOException {
    return objectMapper.readValue(input, clazz);
  }

  public String toJson(Object value) throws IOException {
    return objectMapper.writeValueAsString(value);
  }

  public String prettyPrint(Object value) throws IOException {
    return objectMapper
        .writer(
            new DefaultPrettyPrinter().withArrayIndenter(DefaultIndenter.SYSTEM_LINEFEED_INSTANCE)
        )
        .writeValueAsString(value);
  }

}

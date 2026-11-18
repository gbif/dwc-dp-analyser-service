/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.gbif.dp.service;

import java.time.LocalDateTime;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MessageMapperTest {

  record Timed(LocalDateTime started) {}

  @Test
  void localDateTimeIsStandardIsoReadableByAnyJavaTimeConsumer() throws Exception {
    LocalDateTime started = LocalDateTime.of(2026, 11, 18, 10, 0, 0, 250_000_000);

    String json = Main.getMessageMapper().writeValueAsString(new Timed(started));

    JsonNode node = new ObjectMapper().readTree(json);
    assertEquals("2026-11-18T10:00:00.25", node.get("started").asText());
    ObjectMapper consumer = new ObjectMapper().registerModule(new JavaTimeModule());
    assertEquals(started, consumer.readValue(json, Timed.class).started());
  }

  @Test
  void requestsWithExtraFieldsAreAccepted() throws Exception {
    UUID dataset = UUID.fromString("673f2308-3501-474f-85d5-a832825bb46c");
    String json =
        "{\"datasetUuid\":\"" + dataset + "\",\"attempt\":2,\"header\":{\"stepAttempt\":1}}";

    ValidationRequest request = Main.getMessageMapper().readValue(json, ValidationRequest.class);

    assertEquals(new ValidationRequest(dataset, 2), request);
  }
}

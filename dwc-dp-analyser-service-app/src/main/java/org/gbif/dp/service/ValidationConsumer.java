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

import org.gbif.dp.service.messaging.MessageConsumer;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public class ValidationConsumer {

  private static final Logger log = LoggerFactory.getLogger(ValidationConsumer.class);

  private final MessageConsumer consumer;
  private final ObjectMapper mapper;
  private final ValidationRequestHandler handler;

  public ValidationConsumer(MessageConsumer consumer, ObjectMapper mapper,
                            ValidationRequestHandler handler) {
    this.consumer = consumer;
    this.mapper = mapper;
    this.handler = handler;
  }

  public void start() throws Exception {
    consumer.consume((body, ack) -> {
      String messageId = null;
      UUID datasetUuid = null;
      try {
        JsonNode node = mapper.readTree(body);
        messageId = node.path("messageId").asText(null);
        datasetUuid = UUID.fromString(node.get("datasetUuid").asText());
        int attempt = node.get("attempt").asInt();

        MDC.put("datasetKey", datasetUuid.toString());
        MDC.put("attempt", String.valueOf(attempt));
        MDC.put("step", "Validation");

        handler.handle(new ValidationRequest(datasetUuid, attempt), messageId);

        ack.ack();
      } catch (Exception e) {
        log.error("Failed to process msgId: [{}], dataset: [{}]", messageId, datasetUuid, e);
        ack.nack();
      } finally {
        MDC.clear();
      }
    });
  }
}

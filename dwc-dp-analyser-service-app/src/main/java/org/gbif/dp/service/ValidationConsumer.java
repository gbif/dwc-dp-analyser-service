package org.gbif.dp.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.gbif.dp.service.messaging.MessageConsumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

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

        handler.handle(new ValidationRequest(datasetUuid, attempt), messageId);

        ack.ack();
      } catch (Exception e) {
        // TODO how to handle system errors
        log.error("Failed to process msgId: [{}], dataset: [{}]", messageId, datasetUuid, e);
        ack.nack();
      }
    });
    log.info("Listening for messages");
  }
}

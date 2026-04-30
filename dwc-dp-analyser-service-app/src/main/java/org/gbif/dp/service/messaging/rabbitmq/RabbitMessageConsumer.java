package org.gbif.dp.service.messaging;

import com.rabbitmq.client.Channel;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

public class RabbitMessageConsumer implements MessageConsumer {

  private static final Logger log = LoggerFactory.getLogger(RabbitMessageConsumer.class);

  private final Channel channel;
  private final String queue;

  public RabbitMessageConsumer(Channel channel, String queue) {
    this.channel = channel;
    this.queue = queue;
  }

  @Override
  public void consume(MessageHandler handler) throws IOException {
    channel.basicConsume(queue, false,
      (consumerTag, delivery) -> {
        Ack ack = new Ack() {
          @Override
          public void ack() throws IOException {
            channel.basicAck(delivery.getEnvelope().getDeliveryTag(), false);
          }
          @Override
          public void nack() throws IOException {
            channel.basicNack(delivery.getEnvelope().getDeliveryTag(), false, false);
          }
        };
        try {
          handler.handle(delivery.getBody(), ack);
        } catch (Exception e) {
          log.error("Error handling message body", e);
          ack.nack();
        }
      },
      consumerTag -> {});
  }
}

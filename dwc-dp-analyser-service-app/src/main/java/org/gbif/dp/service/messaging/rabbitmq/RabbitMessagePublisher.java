package org.gbif.dp.service.messaging.rabbitmq;

import com.rabbitmq.client.Channel;
import org.gbif.dp.service.messaging.MessagePublisher;

import java.io.IOException;

public class RabbitMessagePublisher implements MessagePublisher {

  private final Channel channel;
  private final String exchange;
  private final String routingKey;

  public RabbitMessagePublisher(Channel channel, String exchange, String routingKey) {
    this.channel = channel;
    this.exchange = exchange;
    this.routingKey = routingKey;
  }

  @Override
  public void publish(byte[] body) throws IOException {
    channel.basicPublish(exchange, routingKey, null, body);
  }
}

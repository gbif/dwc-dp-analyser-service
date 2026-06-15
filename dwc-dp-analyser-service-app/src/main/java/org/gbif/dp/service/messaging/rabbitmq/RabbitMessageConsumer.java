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
package org.gbif.dp.service.messaging.rabbitmq;

import org.gbif.dp.service.messaging.Ack;
import org.gbif.dp.service.messaging.MessageConsumer;
import org.gbif.dp.service.messaging.MessageHandler;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.rabbitmq.client.Channel;

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

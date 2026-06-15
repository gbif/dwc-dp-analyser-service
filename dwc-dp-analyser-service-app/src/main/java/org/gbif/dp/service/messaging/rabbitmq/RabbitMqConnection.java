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

import org.gbif.dp.service.Config;

import java.io.IOException;
import java.util.concurrent.TimeoutException;

import com.rabbitmq.client.BuiltinExchangeType;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;

public class RabbitMqConnection implements AutoCloseable {

  private final Connection connection;
  private final Channel channel;

  public RabbitMqConnection(Config.RabbitMq cfg) throws IOException, TimeoutException {
    ConnectionFactory factory = new ConnectionFactory();
    factory.setHost(cfg.host);
    factory.setPort(cfg.port);
    factory.setUsername(cfg.user);
    factory.setPassword(cfg.password);
    factory.setVirtualHost(cfg.vhost);

    connection = factory.newConnection();
    channel = connection.createChannel();
    channel.basicQos(1); // one message at a time
    declareTopology(channel, cfg);
  }

  private void declareTopology(Channel channel, Config.RabbitMq cfg) throws IOException {
    channel.queueDeclare(cfg.inputQueue, true, false, false, null);
    channel.exchangeDeclare(cfg.outputExchange, BuiltinExchangeType.TOPIC, true);
  }

  public Channel channel() {
    return channel;
  }

  @Override
  public void close() throws Exception {
    channel.close();
    connection.close(10000);
  }
}

package org.gbif.dp.service.messaging;

import com.rabbitmq.client.BuiltinExchangeType;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import org.gbif.dp.service.Config;

import java.io.IOException;
import java.util.concurrent.TimeoutException;

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

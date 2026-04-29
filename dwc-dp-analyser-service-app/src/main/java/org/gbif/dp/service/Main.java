package org.gbif.dp.service;

import com.fasterxml.jackson.databind.ObjectMapper;

import com.rabbitmq.client.BuiltinExchangeType;
import com.rabbitmq.client.Channel;

import org.gbif.dp.analysis.DataPackageAnalyser;
import org.gbif.dp.analysis.DuckDbDataPackageAnalyser;
import org.gbif.dp.descriptor.JacksonDataPackageParser;
import org.gbif.dp.duckdb.CustomDuckDbConfig;
import org.gbif.dp.duckdb.DuckDbConfig;
import org.gbif.dp.duckdb.DuckDbResourceLoader;
import org.gbif.dp.service.messaging.RabbitMessageConsumer;
import org.gbif.dp.service.messaging.RabbitMessagePublisher;

import org.gbif.dp.service.messaging.RabbitMqConnection;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import picocli.CommandLine;

public class Main {

  private static final Logger log = LoggerFactory.getLogger(Main.class);

  public static void main(String[] args) throws Exception {
    Config config = new Config();
    new CommandLine(config).parseArgs(args);
    run(config);
  }

  private static void run(Config config) throws Exception {
    try (RabbitMqConnection rabbit = new RabbitMqConnection(config.rabbitMq)) {
      Channel channel = rabbit.channel();
      channel.queueDeclare(config.rabbitMq.inputQueue, true, false, false, null);
      channel.exchangeDeclare(config.rabbitMq.outputExchange, BuiltinExchangeType.TOPIC, true);

      ObjectMapper mapper = new ObjectMapper();

      DataPackageAnalyser analyser = new DuckDbDataPackageAnalyser(
        new JacksonDataPackageParser(),
        new DuckDbResourceLoader());

      DuckDbConfig duckDbConfig = new CustomDuckDbConfig("1 GiB", -1, "", "");
      DwcValidator.DwcValidatorConfig validatorConfig = new DwcValidator.DwcValidatorConfig(
        config.archiveRepository, config.unpackRepository, DwcValidator.DwcValidatorConfig.withDuckDbConfig(duckDbConfig)
      );
      DwcValidator validator = new DwcValidator(analyser, validatorConfig);

      ValidationFinishedPublisher publisher = new ValidationFinishedPublisher(
        new RabbitMessagePublisher(channel, config.rabbitMq.outputExchange, config.rabbitMq.outputRoutingKey),
        mapper);

      ValidationRequestHandler handler = new ValidationRequestHandler(validator, publisher);

      DownloadFinishConsumer consumer = new DownloadFinishConsumer(
        new RabbitMessageConsumer(channel, config.rabbitMq.inputQueue),
        mapper,
        handler);

      consumer.start();
      log.info("DwC validation service running.");

      Runtime.getRuntime().addShutdownHook(new Thread(() -> {
        try {
          // TODO add more graceful termination, first closing consumer, and give it time to finish current task
          // consumer.close();
          rabbit.close();
          log.info("RabbitMQ connection closed.");
        } catch (Exception e) {
          log.warn("Error during shutdown", e);
        }
      }));
      Thread.currentThread().join();
    }

  }
}

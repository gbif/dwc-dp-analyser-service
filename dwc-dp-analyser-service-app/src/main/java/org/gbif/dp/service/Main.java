package org.gbif.dp.service;

import com.fasterxml.jackson.databind.ObjectMapper;

import com.rabbitmq.client.Channel;

import org.gbif.dp.analysis.DefaultDataPackageAnalysisOrchestrator;
import org.gbif.dp.analysis.api.DataAnalyser;
import org.gbif.dp.analysis.api.DataPackageAnalysisOrchestrator;
import org.gbif.dp.analysis.api.ValidationOptions;
import org.gbif.dp.analysis.duckdb.DuckDbDataPackageAnalyser;
import org.gbif.dp.analysis.duckdb.DuckDbDialectRenderer;
import org.gbif.dp.analysis.duckdb.DuckDbResourceLoader;
import org.gbif.dp.descriptor.JacksonDataPackageParser;
import org.gbif.dp.duckdb.DuckDbConfig;
import org.gbif.dp.duckdb.DuckDbConfigBuilder;
import org.gbif.dp.service.messaging.rabbitmq.RabbitMessageConsumer;
import org.gbif.dp.service.messaging.rabbitmq.RabbitMessagePublisher;

import org.gbif.dp.service.messaging.rabbitmq.RabbitMqConnection;

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

      ObjectMapper mapper = new ObjectMapper();

      DuckDbConfig duckDbConfig = DuckDbConfigBuilder.defaults()
        .dbMemory(config.duckDbConfig.memory)
        .dbTempDir(config.duckDbConfig.tempDir)
        .build();
      DwcValidator validator = createValidator(config, duckDbConfig);

      ValidationFinishedPublisher publisher = new ValidationFinishedPublisher(
        new RabbitMessagePublisher(channel, config.rabbitMq.outputExchange, config.rabbitMq.outputRoutingKey),
        mapper);

      ValidationRequestHandler handler = new ValidationRequestHandler(validator, publisher);

      ValidationConsumer consumer = new ValidationConsumer(
        new RabbitMessageConsumer(channel, config.rabbitMq.inputQueue),
        mapper,
        handler);

      consumer.start();
      log.info("DwC validation service is listening for messages on queue[{}], publishing results to exchange[{}] with routing-key[{}]",
        config.rabbitMq.inputQueue, config.rabbitMq.outputExchange,  config.rabbitMq.outputRoutingKey);

      Runtime.getRuntime().addShutdownHook(new Thread(() -> {
        try {
          // TODO add more graceful termination, first closing consumer, and give it time to finish current task
          // consumer.close();
          rabbit.close();
          log.info("RabbitMQ connection closed.");
        } catch (Exception e) {
          log.error("Error during shutdown", e);
        }
      }));
      Thread.currentThread().join();
    }

  }

  private static DwcValidator createValidator(Config config, DuckDbConfig duckDbConfig) {
    DataAnalyser analyser = new DuckDbDataPackageAnalyser(
      new JacksonDataPackageParser(),
      new DuckDbResourceLoader(new DuckDbDialectRenderer()),
      duckDbConfig);
    DataPackageAnalysisOrchestrator dataPackageAnalysisOrchestrator =
      new DefaultDataPackageAnalysisOrchestrator(analyser);

    DwcValidator.DwcValidatorConfig validatorConfig = new DwcValidator.DwcValidatorConfig(
      config.archiveRepository, config.unpackRepository, ValidationOptions.defaults()
    );
    DwcValidator validator = new DwcValidator(dataPackageAnalysisOrchestrator, validatorConfig);
    return validator;
  }
}

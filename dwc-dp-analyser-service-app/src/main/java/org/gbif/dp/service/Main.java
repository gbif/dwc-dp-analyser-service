package org.gbif.dp.service;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.gbif.dp.analysis.DataPackageAnalyser;
import org.gbif.dp.analysis.DuckDbDataPackageAnalyser;
import org.gbif.dp.descriptor.JacksonDataPackageParser;
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
      rabbit.channel().queueDeclare(config.rabbitMq.inQueue, true, false, false, null);
      rabbit.channel().queueDeclare(config.rabbitMq.outQueue, true, false, false, null);

      ObjectMapper mapper = new ObjectMapper();

      DataPackageAnalyser analyser = new DuckDbDataPackageAnalyser(
        new JacksonDataPackageParser(),
        new DuckDbResourceLoader());

      DwcValidator validator = new DwcValidator(analyser, config.archiveRepository, config.unpackRepository);

      ValidationFinishedPublisher publisher = new ValidationFinishedPublisher(
        new RabbitMessagePublisher(rabbit.channel(), "", config.rabbitMq.outQueue),
        mapper);

      ValidationRequestHandler handler = new ValidationRequestHandler(validator, publisher);

      DownloadFinishConsumer consumer = new DownloadFinishConsumer(
        new RabbitMessageConsumer(rabbit.channel(), config.rabbitMq.inQueue),
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

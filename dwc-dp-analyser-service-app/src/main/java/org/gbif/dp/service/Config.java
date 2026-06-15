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

import java.nio.file.Path;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(name = "dwcdp-validator", mixinStandardHelpOptions = true)
public class Config {

  @Option(names = "--archive-repository", required = true,
    description = "Directory where downloaded zip archives are stored")
  public Path archiveRepository;

  @Option(names = "--unpack-repository", required = true,
    description = "Directory where archives are unpacked before validation")
  public Path unpackRepository;

  public static class RabbitMq {

    @Option(names = "--rabbit-host", defaultValue = "localhost",
      description = "RabbitMQ host (default: ${DEFAULT-VALUE})")
    public String host;

    @Option(names = "--rabbit-port", defaultValue = "5672",
      description = "RabbitMQ port (default: ${DEFAULT-VALUE})")
    public int port;

    @Option(names = "--rabbit-user", defaultValue = "guest",
      description = "RabbitMQ username (default: ${DEFAULT-VALUE})")
    public String user;

    @Option(names = "--rabbit-password", defaultValue = "guest",
      description = "RabbitMQ password (default: ${DEFAULT-VALUE})")
    public String password;

    @Option(names = "--rabbit-vhost", defaultValue = "/",
      description = "RabbitMQ virtual host (default: ${DEFAULT-VALUE})")
    public String vhost;

    @Option(names = "--input-queue", defaultValue = "dwcdp-validator",
      description = "Queue to consume from (default: ${DEFAULT-VALUE})")
    public String inputQueue;

    @Option(names = "--output-exchange", defaultValue = "crawler",
      description = "Exchange to publish results to (default: ${DEFAULT-VALUE})")
    public String outputExchange;

    @Option(names = "--output-routing-key", defaultValue = "crawl.dwcdp.validation.finished",
      description = "Routing key to publish results to (default: ${DEFAULT-VALUE})")
    public String outputRoutingKey;
  }

  public static class DuckDbConfig {
    @Option(names = "--duckdb-memory", defaultValue = "2GiB",
      description = "DuckDB allowed memory, which is running alongside the JVM")
    public String memory;

    @Option(names = "--duckdb-temp-dir", defaultValue = "",
      description = "DuckDB temp dir for spill to disk, default(empty string) uses duckdb default")
    public String tempDir;
  }

  @CommandLine.ArgGroup(exclusive = false, heading = "RabbitMQ options:%n")
  public RabbitMq rabbitMq = new RabbitMq();

  @CommandLine.ArgGroup(exclusive = false, heading = "DuckDb options:%n")
  public DuckDbConfig duckDbConfig = new DuckDbConfig();
}

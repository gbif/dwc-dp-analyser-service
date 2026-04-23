package org.gbif.dp.service;

public record ValidationRequest(String datasetUuid, int attempt) {
}

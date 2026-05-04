package org.gbif.dp.service;

import java.util.UUID;

public record ValidationRequest(UUID datasetUuid, int attempt) {
}

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.state.deployment;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import io.camunda.zeebe.db.ColumnFamily;
import io.camunda.zeebe.db.TransactionContext;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.db.impl.DbCompositeKey;
import io.camunda.zeebe.db.impl.DbForeignKey;
import io.camunda.zeebe.db.impl.DbInt;
import io.camunda.zeebe.db.impl.DbLong;
import io.camunda.zeebe.db.impl.DbNil;
import io.camunda.zeebe.db.impl.DbString;
import io.camunda.zeebe.db.impl.DbTenantAwareKey;
import io.camunda.zeebe.db.impl.DbTenantAwareKey.PlacementType;
import io.camunda.zeebe.dmn.DecisionEngine;
import io.camunda.zeebe.dmn.DecisionEngineFactory;
import io.camunda.zeebe.dmn.ParsedDecisionRequirementsGraph;
import io.camunda.zeebe.engine.EngineConfiguration;
import io.camunda.zeebe.engine.state.mutable.MutableDecisionState;
import io.camunda.zeebe.protocol.ZbColumnFamilies;
import io.camunda.zeebe.protocol.impl.record.value.deployment.DecisionRecord;
import io.camunda.zeebe.protocol.impl.record.value.deployment.DecisionRequirementsRecord;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import org.agrona.DirectBuffer;

public final class DbDecisionState implements MutableDecisionState {

  private final DecisionEngine decisionEngine = DecisionEngineFactory.createDecisionEngine();

  private final DbString tenantIdKey;
  private final DbLong dbDecisionKey;
  private final DbTenantAwareKey<DbLong> tenantAwareDecisionKey;
  private final DbForeignKey<DbLong> fkDecision;
  private final PersistedDecision dbPersistedDecision;
  private final DbString dbDecisionId;
  private final DbTenantAwareKey<DbString> tenantAwareDecisionId;

  private final DbLong dbDecisionRequirementsKey;
  private final DbTenantAwareKey<DbLong> tenantAwareDecisionRequirementsKey;
  private final DbForeignKey<DbLong> fkDecisionRequirements;
  private final PersistedDecisionRequirements dbPersistedDecisionRequirements;
  private final DbString dbDecisionRequirementsId;
  private final DbTenantAwareKey<DbString> tenantAwareDecisionRequirementsId;
  private final DbCompositeKey<DbForeignKey<DbLong>, DbForeignKey<DbLong>>
      dbDecisionRequirementsKeyAndDecisionKey;

  // TODO
  private final ColumnFamily<DbCompositeKey<DbForeignKey<DbLong>, DbForeignKey<DbLong>>, DbNil>
      decisionKeyByDecisionRequirementsKey;

  private final ColumnFamily<DbTenantAwareKey<DbLong>, PersistedDecision> decisionsByKey;
  private final ColumnFamily<DbTenantAwareKey<DbString>, DbForeignKey<DbLong>>
      latestDecisionKeysByDecisionId;

  private final DbInt dbDecisionVersion;
  private final DbCompositeKey<DbString, DbInt> decisionIdAndVersion;

  // TODO
  private final ColumnFamily<DbCompositeKey<DbString, DbInt>, DbForeignKey<DbLong>>
      decisionKeyByDecisionIdAndVersion;

  private final ColumnFamily<DbTenantAwareKey<DbLong>, PersistedDecisionRequirements>
      decisionRequirementsByKey;
  private final ColumnFamily<DbTenantAwareKey<DbString>, DbForeignKey<DbLong>>
      latestDecisionRequirementsKeysById;

  private final DbInt dbDecisionRequirementsVersion;
  private final DbCompositeKey<DbString, DbInt> decisionRequirementsIdAndVersion;
  private final DbTenantAwareKey<DbCompositeKey<DbString, DbInt>>
      tenantAwareDecisionRequirementsIdAndVersion;

  private final ColumnFamily<
          DbTenantAwareKey<DbCompositeKey<DbString, DbInt>>, DbForeignKey<DbLong>>
      decisionRequirementsKeyByIdAndVersion;

  private final LoadingCache<TenantIdAndDrgKey, DeployedDrg> drgCache;

  public DbDecisionState(
      final ZeebeDb<ZbColumnFamilies> zeebeDb,
      final TransactionContext transactionContext,
      final EngineConfiguration config) {
    tenantIdKey = new DbString();
    dbDecisionKey = new DbLong();
    tenantAwareDecisionKey =
        new DbTenantAwareKey<>(tenantIdKey, dbDecisionKey, PlacementType.PREFIX);
    fkDecision = new DbForeignKey<>(dbDecisionKey, ZbColumnFamilies.DMN_DECISIONS);

    dbPersistedDecision = new PersistedDecision();
    decisionsByKey =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.DMN_DECISIONS,
            transactionContext,
            tenantAwareDecisionKey,
            dbPersistedDecision);

    dbDecisionId = new DbString();
    tenantAwareDecisionId = new DbTenantAwareKey<>(tenantIdKey, dbDecisionId, PlacementType.PREFIX);
    latestDecisionKeysByDecisionId =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.DMN_LATEST_DECISION_BY_ID,
            transactionContext,
            tenantAwareDecisionId,
            fkDecision);

    dbDecisionRequirementsKey = new DbLong();
    tenantAwareDecisionRequirementsKey =
        new DbTenantAwareKey<>(tenantIdKey, dbDecisionRequirementsKey, PlacementType.PREFIX);
    fkDecisionRequirements =
        new DbForeignKey<>(dbDecisionRequirementsKey, ZbColumnFamilies.DMN_DECISION_REQUIREMENTS);
    dbPersistedDecisionRequirements = new PersistedDecisionRequirements();
    decisionRequirementsByKey =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.DMN_DECISION_REQUIREMENTS,
            transactionContext,
            tenantAwareDecisionRequirementsKey,
            dbPersistedDecisionRequirements);

    dbDecisionRequirementsId = new DbString();
    tenantAwareDecisionRequirementsId =
        new DbTenantAwareKey<>(tenantIdKey, dbDecisionRequirementsId, PlacementType.PREFIX);
    latestDecisionRequirementsKeysById =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.DMN_LATEST_DECISION_REQUIREMENTS_BY_ID,
            transactionContext,
            tenantAwareDecisionRequirementsId,
            fkDecisionRequirements);

    dbDecisionRequirementsKeyAndDecisionKey =
        new DbCompositeKey<>(fkDecisionRequirements, fkDecision);
    decisionKeyByDecisionRequirementsKey =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.DMN_DECISION_KEY_BY_DECISION_REQUIREMENTS_KEY,
            transactionContext,
            dbDecisionRequirementsKeyAndDecisionKey,
            DbNil.INSTANCE);

    dbDecisionVersion = new DbInt();
    decisionIdAndVersion = new DbCompositeKey<>(dbDecisionId, dbDecisionVersion);
    decisionKeyByDecisionIdAndVersion =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.DMN_DECISION_KEY_BY_DECISION_ID_AND_VERSION,
            transactionContext,
            decisionIdAndVersion,
            fkDecision);

    dbDecisionRequirementsVersion = new DbInt();
    decisionRequirementsIdAndVersion =
        new DbCompositeKey<>(dbDecisionRequirementsId, dbDecisionRequirementsVersion);
    tenantAwareDecisionRequirementsIdAndVersion =
        new DbTenantAwareKey<>(tenantIdKey, decisionRequirementsIdAndVersion, PlacementType.PREFIX);
    decisionRequirementsKeyByIdAndVersion =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.DMN_DECISION_REQUIREMENTS_KEY_BY_DECISION_REQUIREMENT_ID_AND_VERSION,
            transactionContext,
            tenantAwareDecisionRequirementsIdAndVersion,
            fkDecisionRequirements);

    drgCache =
        CacheBuilder.newBuilder()
            .maximumSize(config.getDrgCacheCapacity())
            .build(
                new CacheLoader<>() {
                  @Override
                  public DeployedDrg load(final TenantIdAndDrgKey tenantIdAndDrgKey)
                      throws DrgNotFoundException {
                    return findAndParseDecisionRequirementsByKeyFromDb(
                        tenantIdAndDrgKey.drgKey, tenantIdAndDrgKey.tenantId);
                  }
                });
  }

  @Override
  public Optional<PersistedDecision> findLatestDecisionByIdAndTenant(
      final DirectBuffer decisionId, final String tenantId) {
    dbDecisionId.wrapBuffer(decisionId);
    tenantIdKey.wrapString(tenantId);

    return Optional.ofNullable(latestDecisionKeysByDecisionId.get(tenantAwareDecisionId))
        .flatMap(
            decisionKey -> findDecisionByTenantAndKey(tenantId, decisionKey.inner().getValue()));
  }

  @Override
  public Optional<PersistedDecision> findDecisionByTenantAndKey(
      final String tenantId, final long decisionKey) {
    dbDecisionKey.wrapLong(decisionKey);
    tenantIdKey.wrapString(tenantId);
    return Optional.ofNullable(decisionsByKey.get(tenantAwareDecisionKey))
        .map(PersistedDecision::copy);
  }

  @Override
  public Optional<DeployedDrg> findLatestDecisionRequirementsByTenantAndId(
      final String tenantId, final DirectBuffer decisionRequirementsId) {
    tenantIdKey.wrapString(tenantId);
    dbDecisionRequirementsId.wrapBuffer(decisionRequirementsId);

    return Optional.ofNullable(
            latestDecisionRequirementsKeysById.get(tenantAwareDecisionRequirementsId))
        .map((requirementsKey) -> requirementsKey.inner().getValue())
        .flatMap(
            decisionRequirementsKey ->
                findDecisionRequirementsByTenantAndKey("", decisionRequirementsKey));
  }

  @Override
  public Optional<DeployedDrg> findDecisionRequirementsByTenantAndKey(
      final String tenantId, final long decisionRequirementsKey) {
    return findDeployedDrg(decisionRequirementsKey, tenantId);
  }

  @Override
  public List<PersistedDecision> findDecisionsByTenantAndDecisionRequirementsKey(
      final String tenantId, final long decisionRequirementsKey) {
    final List<PersistedDecision> decisions = new ArrayList<>();

    dbDecisionRequirementsKey.wrapLong(decisionRequirementsKey);
    decisionKeyByDecisionRequirementsKey.whileEqualPrefix(
        dbDecisionRequirementsKey,
        ((key, nil) -> {
          final var decisionKey = key.second();
          findDecisionByTenantAndKey(tenantId, decisionKey.inner().getValue())
              .ifPresent(decisions::add);
        }));

    return decisions;
  }

  @Override
  public void clearCache() {
    drgCache.invalidateAll();
  }

  private DeployedDrg findAndParseDecisionRequirementsByKeyFromDb(
      final long decisionRequirementsKey, final String tenantId) throws DrgNotFoundException {
    tenantIdKey.wrapString(tenantId);
    dbDecisionRequirementsKey.wrapLong(decisionRequirementsKey);

    final PersistedDecisionRequirements persistedDrg =
        decisionRequirementsByKey.get(tenantAwareDecisionRequirementsKey);
    if (persistedDrg == null) {
      throw new DrgNotFoundException();
    }

    final PersistedDecisionRequirements copiedDrg = persistedDrg.copy();

    final var resourceBytes = BufferUtil.bufferAsArray(copiedDrg.getResource());
    final ParsedDecisionRequirementsGraph parsedDrg =
        decisionEngine.parse(new ByteArrayInputStream(resourceBytes));

    return new DeployedDrg(parsedDrg, copiedDrg);
  }

  private Optional<DeployedDrg> findDeployedDrg(
      final long decisionRequirementsKey, final String tenantId) {
    try {
      // The cache automatically fetches it from the state if the key does not exist.
      return Optional.of(drgCache.get(new TenantIdAndDrgKey(tenantId, decisionRequirementsKey)));
    } catch (final ExecutionException e) {
      // We reach this when we couldn't load the DRG from the state.
      return Optional.empty();
    }
  }

  /**
   * Query decisions to find the key of the decision with the version that comes before the given
   * version.
   *
   * @param decisionId the id of the decision
   * @param currentVersion the current version
   * @return the decision key of the version that's previous to the given version
   */
  private Optional<Long> findPreviousVersionDecisionKey(
      final DirectBuffer decisionId, final int currentVersion) {
    final Map<Integer, Long> decisionKeysByVersion = new HashMap<>();

    dbDecisionId.wrapBuffer(decisionId);
    decisionKeyByDecisionIdAndVersion.whileEqualPrefix(
        dbDecisionId,
        ((key, decisionKey) -> {
          if (key.second().getValue() < currentVersion) {
            decisionKeysByVersion.put(key.second().getValue(), decisionKey.inner().getValue());
          }
        }));

    if (decisionKeysByVersion.isEmpty()) {
      return Optional.empty();
    } else {
      final Integer previousVersion = Collections.max(decisionKeysByVersion.keySet());
      return Optional.of(decisionKeysByVersion.get(previousVersion));
    }
  }

  private Optional<Long> findPreviousVersionDecisionRequirementsKey(
      final DirectBuffer decisionRequirementsId, final int currentVersion, final String tenantId) {
    final Map<Integer, Long> decisionRequirementsKeysByVersion = new HashMap<>();

    tenantIdKey.wrapString(tenantId);
    dbDecisionRequirementsId.wrapBuffer(decisionRequirementsId);
    decisionRequirementsKeyByIdAndVersion.whileEqualPrefix(
        new DbCompositeKey<>(tenantIdKey, dbDecisionRequirementsId),
        ((key, drgKey) -> {
          if (key.wrappedKey().second().getValue() < currentVersion) {
            decisionRequirementsKeysByVersion.put(
                key.wrappedKey().second().getValue(), drgKey.inner().getValue());
          }
        }));

    if (decisionRequirementsKeysByVersion.isEmpty()) {
      return Optional.empty();
    } else {
      final Integer previousVersion = Collections.max(decisionRequirementsKeysByVersion.keySet());
      return Optional.of(decisionRequirementsKeysByVersion.get(previousVersion));
    }
  }

  @Override
  public void storeDecisionRecord(final DecisionRecord record) {
    tenantIdKey.wrapString(record.getTenantId());
    dbDecisionKey.wrapLong(record.getDecisionKey());
    dbPersistedDecision.wrap(record);
    decisionsByKey.upsert(tenantAwareDecisionKey, dbPersistedDecision);

    dbDecisionKey.wrapLong(record.getDecisionKey());
    dbDecisionRequirementsKey.wrapLong(record.getDecisionRequirementsKey());
    decisionKeyByDecisionRequirementsKey.upsert(
        dbDecisionRequirementsKeyAndDecisionKey, DbNil.INSTANCE);

    dbDecisionId.wrapString(record.getDecisionId());
    dbDecisionVersion.wrapInt(record.getVersion());
    decisionKeyByDecisionIdAndVersion.upsert(decisionIdAndVersion, fkDecision);

    updateLatestDecisionVersion(record);
  }

  @Override
  public void storeDecisionRequirements(final DecisionRequirementsRecord record) {
    tenantIdKey.wrapString(record.getTenantId());
    dbDecisionRequirementsKey.wrapLong(record.getDecisionRequirementsKey());
    dbPersistedDecisionRequirements.wrap(record);
    decisionRequirementsByKey.upsert(
        tenantAwareDecisionRequirementsKey, dbPersistedDecisionRequirements);

    dbDecisionRequirementsId.wrapString(record.getDecisionRequirementsId());
    dbDecisionRequirementsVersion.wrapInt(record.getDecisionRequirementsVersion());
    decisionRequirementsKeyByIdAndVersion.upsert(
        tenantAwareDecisionRequirementsIdAndVersion, fkDecisionRequirements);

    updateLatestDecisionRequirementsVersion(record);
  }

  @Override
  public void deleteDecision(final DecisionRecord record) {
    tenantIdKey.wrapString(record.getTenantId());

    findLatestDecisionByIdAndTenant(record.getDecisionIdBuffer(), record.getTenantId())
        .map(PersistedDecision::getVersion)
        .ifPresent(
            latestVersion -> {
              if (latestVersion == record.getVersion()) {
                dbDecisionId.wrapBuffer(record.getDecisionIdBuffer());
                findPreviousVersionDecisionKey(record.getDecisionIdBuffer(), record.getVersion())
                    .ifPresentOrElse(
                        previousDecisionKey -> {
                          // Update the latest decision version
                          dbDecisionKey.wrapLong(previousDecisionKey);
                          latestDecisionKeysByDecisionId.update(tenantAwareDecisionId, fkDecision);
                        },
                        () -> {
                          // Clear the latest decision version
                          latestDecisionKeysByDecisionId.deleteExisting(tenantAwareDecisionId);
                        });
              }
            });

    dbDecisionRequirementsKey.wrapLong(record.getDecisionRequirementsKey());
    dbDecisionKey.wrapLong(record.getDecisionKey());
    dbDecisionId.wrapBuffer(record.getDecisionIdBuffer());
    dbDecisionVersion.wrapInt(record.getVersion());

    decisionKeyByDecisionRequirementsKey.deleteExisting(dbDecisionRequirementsKeyAndDecisionKey);
    decisionsByKey.deleteExisting(tenantAwareDecisionKey);
    decisionKeyByDecisionIdAndVersion.deleteExisting(decisionIdAndVersion);
  }

  @Override
  public void deleteDecisionRequirements(final DecisionRequirementsRecord record) {
    tenantIdKey.wrapString(record.getTenantId());

    findLatestDecisionRequirementsByTenantAndId(
            record.getTenantId(), record.getDecisionRequirementsIdBuffer())
        .map(DeployedDrg::getDecisionRequirementsVersion)
        .ifPresent(
            latestVersion -> {
              if (latestVersion == record.getDecisionRequirementsVersion()) {
                dbDecisionRequirementsId.wrapBuffer(record.getDecisionRequirementsIdBuffer());
                findPreviousVersionDecisionRequirementsKey(
                        record.getDecisionRequirementsIdBuffer(),
                        record.getDecisionRequirementsVersion(),
                        record.getTenantId())
                    .ifPresentOrElse(
                        previousDrgKey -> {
                          // Update the latest decision version
                          dbDecisionRequirementsKey.wrapLong(previousDrgKey);
                          latestDecisionRequirementsKeysById.update(
                              tenantAwareDecisionRequirementsId, fkDecisionRequirements);
                        },
                        () -> {
                          // Clear the latest decision version
                          latestDecisionRequirementsKeysById.deleteExisting(
                              tenantAwareDecisionRequirementsId);
                        });
              }
            });

    dbDecisionRequirementsKey.wrapLong(record.getDecisionRequirementsKey());
    dbDecisionRequirementsId.wrapBuffer(record.getDecisionRequirementsIdBuffer());
    dbDecisionRequirementsVersion.wrapInt(record.getDecisionRequirementsVersion());

    decisionRequirementsByKey.deleteExisting(tenantAwareDecisionRequirementsKey);
    decisionRequirementsKeyByIdAndVersion.deleteExisting(
        tenantAwareDecisionRequirementsIdAndVersion);
    drgCache.invalidate(
        new TenantIdAndDrgKey(record.getTenantId(), record.getDecisionRequirementsKey()));
  }

  private void updateLatestDecisionVersion(final DecisionRecord record) {
    findLatestDecisionByIdAndTenant(record.getDecisionIdBuffer(), record.getTenantId())
        .ifPresentOrElse(
            previousVersion -> {
              if (record.getVersion() > previousVersion.getVersion()) {
                updateDecisionAsLatestVersion(record);
              }
            },
            () -> insertDecisionAsLatestVersion(record));
  }

  private void updateDecisionAsLatestVersion(final DecisionRecord record) {
    tenantIdKey.wrapString(record.getTenantId());
    dbDecisionId.wrapBuffer(record.getDecisionIdBuffer());
    dbDecisionKey.wrapLong(record.getDecisionKey());
    latestDecisionKeysByDecisionId.update(tenantAwareDecisionId, fkDecision);
  }

  private void insertDecisionAsLatestVersion(final DecisionRecord record) {
    tenantIdKey.wrapString(record.getTenantId());
    dbDecisionId.wrapBuffer(record.getDecisionIdBuffer());
    dbDecisionKey.wrapLong(record.getDecisionKey());
    latestDecisionKeysByDecisionId.upsert(tenantAwareDecisionId, fkDecision);
  }

  private void updateLatestDecisionRequirementsVersion(final DecisionRequirementsRecord record) {
    findLatestDecisionRequirementsByTenantAndId(
            record.getTenantId(), record.getDecisionRequirementsIdBuffer())
        .ifPresentOrElse(
            previousVersion -> {
              if (record.getDecisionRequirementsVersion()
                  > previousVersion.getDecisionRequirementsVersion()) {
                updateDecisionRequirementsAsLatestVersion(record);
              }
            },
            () -> insertDecisionRequirementsAsLatestVersion(record));
  }

  private void updateDecisionRequirementsAsLatestVersion(final DecisionRequirementsRecord record) {
    tenantIdKey.wrapString(record.getTenantId());
    dbDecisionRequirementsId.wrapBuffer(record.getDecisionRequirementsIdBuffer());
    dbDecisionRequirementsKey.wrapLong(record.getDecisionRequirementsKey());
    latestDecisionRequirementsKeysById.update(
        tenantAwareDecisionRequirementsId, fkDecisionRequirements);
  }

  private void insertDecisionRequirementsAsLatestVersion(final DecisionRequirementsRecord record) {
    tenantIdKey.wrapString(record.getTenantId());
    dbDecisionRequirementsId.wrapBuffer(record.getDecisionRequirementsIdBuffer());
    dbDecisionRequirementsKey.wrapLong(record.getDecisionRequirementsKey());
    latestDecisionRequirementsKeysById.upsert(
        tenantAwareDecisionRequirementsId, fkDecisionRequirements);
  }

  private record TenantIdAndDrgKey(String tenantId, Long drgKey) {}

  /**
   * This exception is thrown when the drgCache can't find a DRG in the state for a given key. This
   * must be a checked exception, because of the way the {@link LoadingCache} works.
   */
  private static final class DrgNotFoundException extends Exception {}
}

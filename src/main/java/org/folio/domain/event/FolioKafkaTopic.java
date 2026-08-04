package org.folio.domain.event;

import org.folio.kafka.services.KafkaTopic;

import lombok.AllArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.ToString;

@ToString(onlyExplicitlyIncluded = true)
@AllArgsConstructor
public enum FolioKafkaTopic implements KafkaTopic {
  ITEM_CHECKED_OUT("ITEM_CHECKED_OUT", Module.CIRCULATION),
  ITEM_CHECKED_IN("ITEM_CHECKED_IN", Module.CIRCULATION),
  ITEM_DECLARED_LOST("ITEM_DECLARED_LOST", Module.CIRCULATION),
  ITEM_AGED_TO_LOST("ITEM_AGED_TO_LOST", Module.CIRCULATION),
  ITEM_CLAIMED_RETURNED("ITEM_CLAIMED_RETURNED", Module.CIRCULATION),
  LOAN_DUE_DATE_CHANGED("LOAN_DUE_DATE_CHANGED", Module.CIRCULATION),
  LOAN_CLOSED("LOAN_CLOSED", Module.CIRCULATION),
  FEE_FINE_BALANCE_CHANGED("FEE_FINE_BALANCE_CHANGED", Module.FEES_FINES);

  @ToString.Include
  private final String topic;
  private final Module module;

  @Override
  public String moduleName() {
    return module.getName();
  }

  @Override
  public String topicName() {
    return topic;
  }

  @RequiredArgsConstructor
  public enum Module {
    CIRCULATION("circulation"),
    FEES_FINES("feesfines");

    private final String name;

    private String getName() {
      return name;
    }
  }
}


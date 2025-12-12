package de.seuhd.campuscoffee.domain.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for the approval process, in particular the minimum number of approvals required.
 */
@ConfigurationProperties("campus-coffee.approval")
public record ApprovalConfiguration(Integer minCount) {  }

package org.jboss.da.listings.api.service;

import java.util.List;

public interface WLFiller {

    public enum WLStatus {
        PRODUCT_NOT_FOUND, FILLED, ANALYSER_ERROR;
    }

    WLStatus fillWhitelistFromPom(String scmUrl, String revision, String pomPath,
            List<String> repositories, long productId);

    WLStatus fillWhitelistFromGAV(String groupId, String artifactId, String version, long productId);
}

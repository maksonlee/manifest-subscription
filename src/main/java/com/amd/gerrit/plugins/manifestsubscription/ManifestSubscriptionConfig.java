package com.amd.gerrit.plugins.manifestsubscription;

import com.google.gerrit.extensions.annotations.PluginName;
import com.google.gerrit.server.config.PluginConfig;
import com.google.gerrit.server.config.PluginConfigFactory;
import com.google.inject.Inject;

public class ManifestSubscriptionConfig {
    static final String CONFIG_MAX_BRANCHES_PER_REPO = "maxBranchesPerRepo";
    static final String CONFIG_MANIFEST_PATH_PATTERN = "manifestPathPattern";

    static final int DEFAULT_MAX_BRANCHES_PER_REPO = 1000;
    static final String DEFAULT_MANIFEST_PATH_PATTERN = ".*\\.xml";

    @Inject
    private static PluginConfigFactory cfgFactory;

    @Inject
    @PluginName
    private static String pluginName;

    private static int maxBranchesPerRepo;
    private static String manifestPathPattern;

    public static void readConfig() {
        PluginConfig cfg = cfgFactory.getFromGerritConfig(pluginName);
        maxBranchesPerRepo = cfg.getInt(CONFIG_MAX_BRANCHES_PER_REPO,
                DEFAULT_MAX_BRANCHES_PER_REPO);
        manifestPathPattern = cfg.getString(CONFIG_MANIFEST_PATH_PATTERN,
                DEFAULT_MANIFEST_PATH_PATTERN);
    }

    public static int getMaxBranchesPerRepo() {
        return maxBranchesPerRepo;
    }

    public static String getManifestPathPattern() {
        return manifestPathPattern;
    }
}
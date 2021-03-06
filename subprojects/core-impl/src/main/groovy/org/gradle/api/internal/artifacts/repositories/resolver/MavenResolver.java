/*
 * Copyright 2012 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.gradle.api.internal.artifacts.repositories.resolver;

import com.google.common.collect.ImmutableSet;
import org.apache.ivy.plugins.matcher.PatternMatcher;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.artifacts.result.Artifact;
import org.gradle.api.internal.artifacts.ivyservice.BuildableArtifactSetResolveResult;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.BuildableModuleVersionMetaDataResolveResult;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.ModuleComponentRepositoryAccess;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.ModuleSource;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.parser.GradlePomModuleDescriptorParser;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.ResolverStrategy;
import org.gradle.api.internal.artifacts.metadata.*;
import org.gradle.api.internal.artifacts.repositories.transport.RepositoryTransport;
import org.gradle.api.internal.artifacts.result.metadata.MavenPomArtifact;
import org.gradle.api.internal.externalresource.local.LocallyAvailableResourceFinder;
import org.gradle.api.internal.resource.ResourceNotFoundException;
import org.gradle.api.resources.ResourceException;
import org.gradle.util.DeprecationLogger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.*;

public class MavenResolver extends ExternalResourceResolver implements PatternBasedResolver {
    private static final Logger LOGGER = LoggerFactory.getLogger(MavenResolver.class);
    private final RepositoryTransport transport;
    private final String root;
    private final List<String> artifactRoots = new ArrayList<String>();
    private String pattern = MavenPattern.M2_PATTERN;
    private boolean usepoms = true;
    private boolean useMavenMetadata = true;
    private final MavenMetadataLoader mavenMetaDataLoader;

    public MavenResolver(String name, URI rootUri, RepositoryTransport transport,
                         LocallyAvailableResourceFinder<ModuleVersionArtifactMetaData> locallyAvailableResourceFinder,
                         ResolverStrategy resolverStrategy) {
        super(name, transport.getRepository(),
                new ChainedVersionLister(new MavenVersionLister(transport.getRepository()), new ResourceVersionLister(transport.getRepository())),
                locallyAvailableResourceFinder, new GradlePomModuleDescriptorParser(), resolverStrategy);
        transport.configureCacheManager(this);

        this.mavenMetaDataLoader = new MavenMetadataLoader(transport.getRepository());
        this.transport = transport;
        this.root = transport.convertToPath(rootUri);

        super.setM2compatible(true);

        // SNAPSHOT revisions are changing revisions
        setChangingMatcher(PatternMatcher.REGEXP);
        setChangingPattern(".*-SNAPSHOT");

        updatePatterns();
    }

    protected void doResolveComponentMetaData(DependencyMetaData dependency, ModuleComponentIdentifier moduleComponentIdentifier, BuildableModuleVersionMetaDataResolveResult result) {
        if (isSnapshotVersion(moduleComponentIdentifier)) {
            final MavenUniqueSnapshotModuleSource uniqueSnapshotVersion = findUniqueSnapshotVersion(moduleComponentIdentifier);
            if (uniqueSnapshotVersion != null) {
                resolveUniqueSnapshotDependency(dependency, moduleComponentIdentifier, result, uniqueSnapshotVersion);
                return;
            }
        }

        resolveStaticDependency(dependency, moduleComponentIdentifier, result, super.createArtifactResolver());
    }

    @Override
    protected boolean isMetaDataArtifact(Class<? extends Artifact> artifactType) {
        return artifactType == MavenPomArtifact.class;
    }

    private void resolveUniqueSnapshotDependency(DependencyMetaData dependency, ModuleComponentIdentifier module, BuildableModuleVersionMetaDataResolveResult result, MavenUniqueSnapshotModuleSource snapshotSource) {
        resolveStaticDependency(dependency, module, result, createArtifactResolver(snapshotSource));
        if (result.getState() == BuildableModuleVersionMetaDataResolveResult.State.Resolved) {
            result.setModuleSource(snapshotSource);
        }
    }

    private boolean isSnapshotVersion(ModuleComponentIdentifier module) {
        return module.getVersion().endsWith("SNAPSHOT");
    }

    @Override
    protected ExternalResourceArtifactResolver createArtifactResolver(ModuleSource moduleSource) {

        if (moduleSource instanceof MavenUniqueSnapshotModuleSource) {
            final String timestamp = ((MavenUniqueSnapshotModuleSource) moduleSource).getTimestamp();
            return new MavenUniqueSnapshotExternalResourceArtifactResolver(super.createArtifactResolver(moduleSource), timestamp);
        }

        return super.createArtifactResolver(moduleSource);
    }

    public void addArtifactLocation(URI baseUri, String pattern) {
        if (pattern != null && pattern.length() > 0) {
            throw new IllegalArgumentException("Maven Resolver only supports a single pattern. It cannot be provided on a per-location basis.");
        }
        artifactRoots.add(transport.convertToPath(baseUri));

        updatePatterns();
    }

    public void addDescriptorLocation(URI baseUri, String pattern) {
        throw new UnsupportedOperationException("Cannot have multiple descriptor urls for MavenResolver");
    }

    private String getWholePattern() {
        return root + pattern;
    }

    private void updatePatterns() {
        if (isUsepoms()) {
            setIvyPatterns(Collections.singletonList(getWholePattern()));
        } else {
            setIvyPatterns(Collections.<String>emptyList());
        }

        List<String> artifactPatterns = new ArrayList<String>();
        artifactPatterns.add(getWholePattern());
        for (String artifactRoot : artifactRoots) {
            artifactPatterns.add(artifactRoot + pattern);
        }
        setArtifactPatterns(artifactPatterns);
    }

    @Override
    protected IvyArtifactName getMetaDataArtifactName(String moduleName) {
        if (isUsepoms()) {
            return new DefaultIvyArtifactName(moduleName, "pom", "pom");
        }

        return null;
    }

    private MavenUniqueSnapshotModuleSource findUniqueSnapshotVersion(ModuleComponentIdentifier module) {
        String metadataLocation = toResourcePattern(getWholePattern()).toModuleVersionPath(module) + "/maven-metadata.xml";
        MavenMetadata mavenMetadata = parseMavenMetadata(metadataLocation);

        if (mavenMetadata.timestamp != null) {
            // we have found a timestamp, so this is a snapshot unique version
            String timestamp = String.format("%s-%s", mavenMetadata.timestamp, mavenMetadata.buildNumber);
            return new MavenUniqueSnapshotModuleSource(timestamp);
        }
        return null;
    }

    private MavenMetadata parseMavenMetadata(String metadataLocation) {
        if (shouldUseMavenMetadata(pattern)) {
            try {
                return mavenMetaDataLoader.load(metadataLocation);
            } catch (ResourceNotFoundException e) {
                return new MavenMetadata();
            } catch (ResourceException e) {
                LOGGER.warn("impossible to access Maven metadata file, ignored.", e);
            }
        }
        return new MavenMetadata();
    }

    // A bunch of configuration properties that we don't (yet) support in our model via the DSL. Users can still tweak these on the resolver using mavenRepo().
    public boolean isUsepoms() {
        return usepoms;
    }

    public void setUsepoms(boolean usepoms) {
        this.usepoms = usepoms;
        updatePatterns();
    }

    public boolean isUseMavenMetadata() {
        return useMavenMetadata;
    }

    @Deprecated
    public void setUseMavenMetadata(boolean useMavenMetadata) {
        DeprecationLogger.nagUserOfDiscontinuedMethod("MavenResolver.setUseMavenMetadata(boolean)");
        this.useMavenMetadata = useMavenMetadata;
        if (useMavenMetadata) {
            this.versionLister = new ChainedVersionLister(
                    new MavenVersionLister(getRepository()),
                    new ResourceVersionLister(getRepository()));
        } else {
            this.versionLister = new ResourceVersionLister(getRepository());
        }
    }

    private boolean shouldUseMavenMetadata(String pattern) {
        return isUseMavenMetadata() && pattern.endsWith(MavenPattern.M2_PATTERN);
    }

    public String getPattern() {
        return pattern;
    }

    public void setPattern(String pattern) {
        if (pattern == null) {
            throw new NullPointerException("pattern must not be null");
        }
        this.pattern = pattern;
        updatePatterns();
    }

    public String getRoot() {
        return root;
    }

    public void setRoot(String root) {
        throw new UnsupportedOperationException("Cannot configure root on mavenRepo. Use 'url' property instead.");
    }

    @Override
    public void setM2compatible(boolean compatible) {
        if (!compatible) {
            throw new IllegalArgumentException("Cannot set m2compatible = false on mavenRepo.");
        }
    }

    public ModuleComponentRepositoryAccess getLocalAccess() {
        return new MavenLocalRepositoryAccess();
    }

    public ModuleComponentRepositoryAccess getRemoteAccess() {
        return new MavenRemoteRepositoryAccess();
    }

    private class MavenLocalRepositoryAccess extends LocalRepositoryAccess {
        @Override
        protected void resolveConfigurationArtifacts(ModuleVersionMetaData module, ConfigurationMetaData configuration, BuildableArtifactSetResolveResult result) {
            MavenModuleVersionMetaData mavenMetaData = module.getMavenMetaData();

            if (mavenMetaData.isKnownJarPackaging()) {
                ModuleVersionArtifactMetaData artifact = module.artifact("jar", "jar", null);
                result.resolved(ImmutableSet.of(artifact));
            }
        }

        @Override
        protected void resolveJavadocArtifacts(ModuleVersionMetaData module, BuildableArtifactSetResolveResult result) {
            // Javadoc artifacts are optional, so we need to probe for them remotely
        }

        @Override
        protected void resolveSourceArtifacts(ModuleVersionMetaData module, BuildableArtifactSetResolveResult result) {
            // Javadoc artifacts are optional, so we need to probe for them remotely
        }
    }

    private class MavenRemoteRepositoryAccess extends RemoteRepositoryAccess {
        @Override
        protected void resolveConfigurationArtifacts(ModuleVersionMetaData module, ConfigurationMetaData configuration, BuildableArtifactSetResolveResult result) {
            MavenModuleVersionMetaData mavenMetaData = module.getMavenMetaData();

            if (mavenMetaData.isPomPackaging()) {
                Set<ComponentArtifactMetaData> artifacts = new LinkedHashSet<ComponentArtifactMetaData>();
                artifacts.addAll(findOptionalArtifacts(module, "jar", null));
                result.resolved(artifacts);
            } else {
                ModuleVersionArtifactMetaData artifactMetaData = module.artifact(mavenMetaData.getPackaging(), mavenMetaData.getPackaging(), null);

                if (createArtifactResolver(module.getSource()).artifactExists(artifactMetaData)) {
                    DeprecationLogger.nagUserOfDeprecated("Relying on packaging to define the extension of the main artifact");
                    result.resolved(ImmutableSet.of(artifactMetaData));
                } else {
                    ModuleVersionArtifactMetaData artifact = module.artifact("jar", "jar", null);
                    result.resolved(ImmutableSet.of(artifact));
                }
            }
        }

        @Override
        protected void resolveJavadocArtifacts(ModuleVersionMetaData module, BuildableArtifactSetResolveResult result) {
            result.resolved(findOptionalArtifacts(module, "javadoc", "javadoc"));
        }

        @Override
        protected void resolveSourceArtifacts(ModuleVersionMetaData module, BuildableArtifactSetResolveResult result) {
            result.resolved(findOptionalArtifacts(module, "source", "sources"));
        }
    }
}

/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.api.internal.artifacts.ivyservice.ivyresolve.parser

import org.gradle.api.internal.artifacts.result.metadata.MavenPomArtifact
import org.gradle.api.internal.externalresource.DefaultLocallyAvailableExternalResource
import org.gradle.internal.id.UUIDGenerator
import org.gradle.internal.resource.local.DefaultLocallyAvailableResource

import static org.gradle.api.internal.artifacts.ivyservice.IvyUtil.createModuleId

class GradlePomModuleDescriptorParserProfileTest extends AbstractGradlePomModuleDescriptorParserTest {
    def "pom with project coordinates defined by active profile properties"() {
        given:
        pomFile << """
<project>
    <modelVersion>4.0.0</modelVersion>
    <groupId>\${some.group}</groupId>
    <artifactId>\${some.artifact}</artifactId>
    <version>\${some.version}</version>

    <profiles>
        <profile>
            <id>profile-1</id>
            <activation>
                <activeByDefault>true</activeByDefault>
            </activation>
            <properties>
                <some.group>group-one</some.group>
                <some.artifact>artifact-one</some.artifact>
                <some.version>version-one</some.version>
            </properties>
        </profile>
    </profiles>
</project>
"""

        when:
        def descriptor = parsePom()

        then:
        descriptor.moduleRevisionId == moduleId('group-one', 'artifact-one', 'version-one')
        descriptor.dependencies.length == 0
    }

    def "pom with dependency coordinates defined by active profile properties"() {
        given:
        pomFile << """
<project>
    <modelVersion>4.0.0</modelVersion>
    <groupId>group-one</groupId>
    <artifactId>artifact-one</artifactId>
    <version>version-one</version>

    <dependencies>
        <dependency>
            <groupId>\${some.group}</groupId>
            <artifactId>\${some.artifact}</artifactId>
            <version>\${some.version}</version>
        </dependency>
    </dependencies>

    <profiles>
        <profile>
            <id>profile-1</id>
            <activation>
                <activeByDefault>true</activeByDefault>
            </activation>
            <properties>
                <some.group>group-two</some.group>
                <some.artifact>artifact-two</some.artifact>
                <some.version>version-two</some.version>
            </properties>
        </profile>
    </profiles>
</project>
"""

        when:
        def descriptor = parsePom()

        then:
        descriptor.moduleRevisionId == moduleId('group-one', 'artifact-one', 'version-one')
        descriptor.dependencies.length == 1
        descriptor.dependencies.first().dependencyRevisionId == moduleId('group-two', 'artifact-two', 'version-two')
        hasDefaultDependencyArtifact(descriptor.dependencies.first())
    }

    def "uses parent properties from active profile to provide default values for a dependency"() {
        given:
        def parent = tmpDir.file("parent.xlm") << """
<project>
    <modelVersion>4.0.0</modelVersion>
    <groupId>group-one</groupId>
    <artifactId>artifact-one</artifactId>
    <version>version-one</version>

    <profiles>
        <profile>
            <id>profile-1</id>
            <activation>
                <activeByDefault>true</activeByDefault>
            </activation>
            <properties>
                <some.group>group-two</some.group>
                <some.artifact>artifact-two</some.artifact>
                <some.version>version-two</some.version>
            </properties>
        </profile>
    </profiles>
</project>
"""

        pomFile << """
<project>
    <modelVersion>4.0.0</modelVersion>
    <groupId>group-one</groupId>
    <artifactId>artifact-one</artifactId>
    <version>version-one</version>

    <parent>
        <groupId>group-one</groupId>
        <artifactId>parent</artifactId>
        <version>version-one</version>
    </parent>

    <dependencies>
        <dependency>
            <groupId>\${some.group}</groupId>
            <artifactId>\${some.artifact}</artifactId>
            <version>\${some.version}</version>
        </dependency>
    </dependencies>
</project>
"""
        and:
        parseContext.getMetaDataArtifact(_, MavenPomArtifact.class) >> { new DefaultLocallyAvailableExternalResource(parent.toURI().toURL().toString(), new DefaultLocallyAvailableResource(parent)) }

        when:
        def descriptor = parsePom()

        then:
        descriptor.dependencies.length == 1
        def dep = descriptor.dependencies.first()
        dep.dependencyRevisionId == moduleId('group-two', 'artifact-two', 'version-two')
        dep.moduleConfigurations == ['compile', 'runtime']
        hasDefaultDependencyArtifact(dep)
    }

    def "uses grand parent properties from active profile to provide default values for a dependency"() {
        given:
        def grandParent = tmpDir.file("grandparent.xml") << """
<project>
    <groupId>different-group</groupId>
    <artifactId>grandparent</artifactId>
    <version>different-version</version>

    <profiles>
        <profile>
            <id>profile-1</id>
            <activation>
                <activeByDefault>true</activeByDefault>
            </activation>
            <properties>
                <some.group>group-two</some.group>
                <some.artifact>artifact-two</some.artifact>
                <some.version>version-two</some.version>
            </properties>
        </profile>
    </profiles>
</project>
"""

        def parent = tmpDir.file("parent.xml") << """
<project>
    <modelVersion>4.0.0</modelVersion>
    <groupId>group-one</groupId>
    <artifactId>artifact-one</artifactId>
    <version>version-one</version>

    <parent>
        <groupId>different-group</groupId>
        <artifactId>grandparent</artifactId>
        <version>different-version</version>
    </parent>
</project>
"""

        pomFile << """
<project>
    <modelVersion>4.0.0</modelVersion>
    <groupId>group-one</groupId>
    <artifactId>artifact-one</artifactId>
    <version>version-one</version>

    <parent>
        <groupId>group-one</groupId>
        <artifactId>parent</artifactId>
        <version>version-one</version>
    </parent>

    <dependencies>
        <dependency>
            <groupId>\${some.group}</groupId>
            <artifactId>\${some.artifact}</artifactId>
            <version>\${some.version}</version>
        </dependency>
    </dependencies>
</project>
"""
        and:
        parseContext.getMetaDataArtifact({ it.name == 'parent' }, MavenPomArtifact) >> { new DefaultLocallyAvailableExternalResource(parent.toURI().toURL().toString(), new DefaultLocallyAvailableResource(parent)) }
        parseContext.getMetaDataArtifact({ it.name == 'grandparent' }, MavenPomArtifact) >> { new DefaultLocallyAvailableExternalResource(grandParent.toURI().toURL().toString(), new DefaultLocallyAvailableResource(grandParent)) }

        when:
        def descriptor = parsePom()

        then:
        descriptor.dependencies.length == 1
        def dep = descriptor.dependencies.first()
        dep.dependencyRevisionId == moduleId('group-two', 'artifact-two', 'version-two')
        dep.moduleConfigurations == ['compile', 'runtime']
        hasDefaultDependencyArtifact(dep)
    }

    def "uses dependency management section in active profile to provide default values for a dependency"() {
        given:
        pomFile << """
<project>
    <modelVersion>4.0.0</modelVersion>
    <groupId>group-one</groupId>
    <artifactId>artifact-one</artifactId>
    <version>version-one</version>

    <dependencies>
        <dependency>
            <groupId>group-two</groupId>
            <artifactId>artifact-two</artifactId>
        </dependency>
    </dependencies>

    <profiles>
        <profile>
            <id>profile-1</id>
            <activation>
                <activeByDefault>true</activeByDefault>
            </activation>
            <dependencyManagement>
                <dependencies>
                    <dependency>
                        <groupId>group-two</groupId>
                        <artifactId>artifact-two</artifactId>
                        <version>1.2</version>
                        <scope>test</scope>
                        <exclusions>
                            <exclusion>
                                <groupId>group-three</groupId>
                                <artifactId>artifact-three</artifactId>
                            </exclusion>
                        </exclusions>
                    </dependency>
                </dependencies>
            </dependencyManagement>
        </profile>
    </profiles>
</project>
"""

        when:
        def descriptor = parsePom()

        then:
        descriptor.dependencies.length == 1
        def dep = descriptor.dependencies.first()
        dep.dependencyRevisionId == moduleId('group-two', 'artifact-two', '1.2')
        dep.moduleConfigurations == ['test']
        dep.allExcludeRules.length == 1
        dep.allExcludeRules.first().id.moduleId == createModuleId('group-three', 'artifact-three')
        hasDefaultDependencyArtifact(dep)
    }

    def "uses parent pom dependency management section in active profile to provide default values for a dependency"() {
        given:
        def parent = tmpDir.file("parent.xml") << """
<project>
    <modelVersion>4.0.0</modelVersion>
    <groupId>group-one</groupId>
    <artifactId>artifact-one</artifactId>
    <version>version-one</version>

    <profiles>
        <profile>
            <id>profile-1</id>
            <activation>
                <activeByDefault>true</activeByDefault>
            </activation>
            <dependencyManagement>
                <dependencies>
                    <dependency>
                        <groupId>group-two</groupId>
                        <artifactId>artifact-two</artifactId>
                        <version>1.2</version>
                    </dependency>
                </dependencies>
            </dependencyManagement>
        </profile>
    </profiles>
</project>
"""

        pomFile << """
<project>
    <modelVersion>4.0.0</modelVersion>
    <groupId>group-one</groupId>
    <artifactId>artifact-one</artifactId>
    <version>version-one</version>

    <parent>
        <groupId>group-one</groupId>
        <artifactId>parent</artifactId>
        <version>version-one</version>
    </parent>

    <dependencies>
        <dependency>
            <groupId>group-two</groupId>
            <artifactId>artifact-two</artifactId>
        </dependency>
    </dependencies>
</project>
"""
        and:
        parseContext.getMetaDataArtifact(_, MavenPomArtifact) >> { new DefaultLocallyAvailableExternalResource(parent.toURI().toURL().toString(), new DefaultLocallyAvailableResource(parent)) }

        when:
        def descriptor = parsePom()

        then:
        descriptor.dependencies.length == 1
        def dep = descriptor.dependencies.first()
        dep.dependencyRevisionId == moduleId('group-two', 'artifact-two', '1.2')
        dep.moduleConfigurations == ['compile', 'runtime']
        hasDefaultDependencyArtifact(dep)
    }

    def "uses grand parent pom dependency management section in active profile to provide default values for a dependency"() {
        given:
        def grandParent = tmpDir.file("grandparent.xml") << """
<project>
    <groupId>different-group</groupId>
    <artifactId>grandparent</artifactId>
    <version>different-version</version>

    <profiles>
        <profile>
            <id>profile-1</id>
            <activation>
                <activeByDefault>true</activeByDefault>
            </activation>
            <dependencyManagement>
                <dependencies>
                    <dependency>
                        <groupId>group-two</groupId>
                        <artifactId>artifact-two</artifactId>
                        <version>1.2</version>
                    </dependency>
                </dependencies>
            </dependencyManagement>
        </profile>
    </profiles>
</project>
"""

        def parent = tmpDir.file("parent.xml") << """
<project>
    <modelVersion>4.0.0</modelVersion>
    <groupId>group-one</groupId>
    <artifactId>artifact-one</artifactId>
    <version>version-one</version>

    <parent>
        <groupId>different-group</groupId>
        <artifactId>grandparent</artifactId>
        <version>different-version</version>
    </parent>
</project>
"""

        pomFile << """
<project>
    <modelVersion>4.0.0</modelVersion>
    <groupId>group-one</groupId>
    <artifactId>artifact-one</artifactId>
    <version>version-one</version>

    <parent>
        <groupId>group-one</groupId>
        <artifactId>parent</artifactId>
        <version>version-one</version>
    </parent>

    <dependencies>
        <dependency>
            <groupId>group-two</groupId>
            <artifactId>artifact-two</artifactId>
        </dependency>
    </dependencies>
</project>
"""
        and:
        parseContext.getMetaDataArtifact({ it.name == 'parent' }, MavenPomArtifact) >> { new DefaultLocallyAvailableExternalResource(parent.toURI().toURL().toString(), new DefaultLocallyAvailableResource(parent)) }
        parseContext.getMetaDataArtifact({ it.name == 'grandparent' }, MavenPomArtifact) >> { new DefaultLocallyAvailableExternalResource(grandParent.toURI().toURL().toString(), new DefaultLocallyAvailableResource(grandParent)) }

        when:
        def descriptor = parsePom()

        then:
        descriptor.dependencies.length == 1
        def dep = descriptor.dependencies.first()
        dep.dependencyRevisionId == moduleId('group-two', 'artifact-two', '1.2')
        dep.moduleConfigurations == ['compile', 'runtime']
        hasDefaultDependencyArtifact(dep)
    }

    def "uses dependency management section from imported POM in active profile to define defaults for main POM body dependency"() {
        given:
        def imported = tmpDir.file("imported.xml") << """
<project>
    <modelVersion>4.0.0</modelVersion>
    <groupId>different-group</groupId>
    <artifactId>imported</artifactId>
    <version>different-version</version>

    <dependencyManagement>
        <dependencies>
            <dependency>
                <groupId>group-two</groupId>
                <artifactId>artifact-two</artifactId>
                <version>1.2</version>
            </dependency>
        </dependencies>
    </dependencyManagement>
</project>
"""

        pomFile << """
<project>
    <modelVersion>4.0.0</modelVersion>
    <groupId>group-one</groupId>
    <artifactId>artifact-one</artifactId>
    <version>version-one</version>

    <dependencies>
        <dependency>
            <groupId>group-two</groupId>
            <artifactId>artifact-two</artifactId>
        </dependency>
    </dependencies>

    <profiles>
        <profile>
            <id>profile-1</id>
            <activation>
                <activeByDefault>true</activeByDefault>
            </activation>
            <dependencyManagement>
                <dependencies>
                    <dependency>
                        <groupId>different-group</groupId>
                        <artifactId>imported</artifactId>
                        <version>different-version</version>
                        <type>pom</type>
                        <scope>import</scope>
                    </dependency>
                </dependencies>
            </dependencyManagement>
        </profile>
    </profiles>
</project>
"""
        and:
        parseContext.getMetaDataArtifact({ it.name == 'imported' }, MavenPomArtifact) >> { new DefaultLocallyAvailableExternalResource(imported.toURI().toURL().toString(), new DefaultLocallyAvailableResource(imported)) }

        when:
        def descriptor = parsePom()

        then:
        descriptor.dependencies.length == 1
        def dep = descriptor.dependencies.first()
        dep.dependencyRevisionId == moduleId('group-two', 'artifact-two', '1.2')
        dep.moduleConfigurations == ['compile', 'runtime']
        hasDefaultDependencyArtifact(dep)
    }

    def "pom with dependency defined by active profile"() {
        given:
        pomFile << """
<project>
    <modelVersion>4.0.0</modelVersion>
    <groupId>group-one</groupId>
    <artifactId>artifact-one</artifactId>
    <version>version-one</version>

    <profiles>
        <profile>
            <id>profile-1</id>
            <activation>
                <activeByDefault>true</activeByDefault>
            </activation>
            <dependencies>
                <dependency>
                    <groupId>group-two</groupId>
                    <artifactId>artifact-two</artifactId>
                    <version>version-two</version>
                </dependency>
            </dependencies>
        </profile>
    </profiles>
</project>
"""

        when:
        def descriptor = parsePom()

        then:
        descriptor.moduleRevisionId == moduleId('group-one', 'artifact-one', 'version-one')
        descriptor.dependencies.length == 1
        descriptor.dependencies.first().dependencyRevisionId == moduleId('group-two', 'artifact-two', 'version-two')
        hasDefaultDependencyArtifact(descriptor.dependencies.first())
    }

    def "uses parent dependency from active profile"() {
        given:
        def parent = tmpDir.file("parent.xlm") << """
<project>
    <modelVersion>4.0.0</modelVersion>
    <groupId>group-one</groupId>
    <artifactId>artifact-one</artifactId>
    <version>version-one</version>

    <profiles>
        <profile>
            <id>profile-1</id>
            <activation>
                <activeByDefault>true</activeByDefault>
            </activation>
            <dependencies>
                <dependency>
                    <groupId>group-two</groupId>
                    <artifactId>artifact-two</artifactId>
                    <version>version-two</version>
                </dependency>
            </dependencies>
        </profile>
    </profiles>
</project>
"""

        pomFile << """
<project>
    <modelVersion>4.0.0</modelVersion>
    <groupId>group-one</groupId>
    <artifactId>artifact-one</artifactId>
    <version>version-one</version>

    <parent>
        <groupId>group-one</groupId>
        <artifactId>parent</artifactId>
        <version>version-one</version>
    </parent>
</project>
"""
        and:
        parseContext.getMetaDataArtifact(_, MavenPomArtifact) >> { new DefaultLocallyAvailableExternalResource(parent.toURI().toURL().toString(), new DefaultLocallyAvailableResource(parent)) }

        when:
        def descriptor = parsePom()

        then:
        descriptor.dependencies.length == 1
        def dep = descriptor.dependencies.first()
        dep.dependencyRevisionId == moduleId('group-two', 'artifact-two', 'version-two')
        dep.moduleConfigurations == ['compile', 'runtime']
        hasDefaultDependencyArtifact(dep)
    }

    def "uses grand parent dependency from active profile"() {
        given:
        def grandParent = tmpDir.file("grandparent.xml") << """
<project>
    <groupId>different-group</groupId>
    <artifactId>grandparent</artifactId>
    <version>different-version</version>

    <profiles>
        <profile>
            <id>profile-1</id>
            <activation>
                <activeByDefault>true</activeByDefault>
            </activation>
            <dependencies>
                <dependency>
                    <groupId>group-two</groupId>
                    <artifactId>artifact-two</artifactId>
                    <version>1.2</version>
                </dependency>
            </dependencies>
        </profile>
    </profiles>
</project>
"""

        def parent = tmpDir.file("parent.xml") << """
<project>
    <modelVersion>4.0.0</modelVersion>
    <groupId>group-one</groupId>
    <artifactId>artifact-one</artifactId>
    <version>version-one</version>

    <parent>
        <groupId>different-group</groupId>
        <artifactId>grandparent</artifactId>
        <version>different-version</version>
    </parent>
</project>
"""

        pomFile << """
<project>
    <modelVersion>4.0.0</modelVersion>
    <groupId>group-one</groupId>
    <artifactId>artifact-one</artifactId>
    <version>version-one</version>

    <parent>
        <groupId>group-one</groupId>
        <artifactId>parent</artifactId>
        <version>version-one</version>
    </parent>
</project>
"""
        and:
        parseContext.getMetaDataArtifact({ it.name == 'parent' }, MavenPomArtifact) >> { new DefaultLocallyAvailableExternalResource(parent.toURI().toURL().toString(), new DefaultLocallyAvailableResource(parent)) }
        parseContext.getMetaDataArtifact({ it.name == 'grandparent' }, MavenPomArtifact) >> { new DefaultLocallyAvailableExternalResource(grandParent.toURI().toURL().toString(), new DefaultLocallyAvailableResource(grandParent)) }

        when:
        def descriptor = parsePom()

        then:
        descriptor.dependencies.length == 1
        def dep = descriptor.dependencies.first()
        dep.dependencyRevisionId == moduleId('group-two', 'artifact-two', '1.2')
        dep.moduleConfigurations == ['compile', 'runtime']
        hasDefaultDependencyArtifact(dep)
    }

    def "uses parent dependency over grand parent dependency with same groupId and artifactId from active profile"() {
        given:
        def grandParent = tmpDir.file("grandparent.xml") << """
<project>
    <groupId>different-group</groupId>
    <artifactId>grandparent</artifactId>
    <version>different-version</version>

    <profiles>
        <profile>
            <id>profile-1</id>
            <activation>
                <activeByDefault>true</activeByDefault>
            </activation>
            <dependencies>
                <dependency>
                    <groupId>group-two</groupId>
                    <artifactId>artifact-two</artifactId>
                    <version>1.2</version>
                </dependency>
            </dependencies>
        </profile>
    </profiles>
</project>
"""

        def parent = tmpDir.file("parent.xml") << """
<project>
    <modelVersion>4.0.0</modelVersion>
    <groupId>group-one</groupId>
    <artifactId>artifact-one</artifactId>
    <version>version-one</version>

    <parent>
        <groupId>different-group</groupId>
        <artifactId>grandparent</artifactId>
        <version>different-version</version>
    </parent>

    <profiles>
        <profile>
            <id>profile-2</id>
            <activation>
                <activeByDefault>true</activeByDefault>
            </activation>
            <dependencies>
                <dependency>
                    <groupId>group-two</groupId>
                    <artifactId>artifact-two</artifactId>
                    <version>1.3</version>
                </dependency>
            </dependencies>
        </profile>
    </profiles>
</project>
"""

        pomFile << """
<project>
    <modelVersion>4.0.0</modelVersion>
    <groupId>group-one</groupId>
    <artifactId>artifact-one</artifactId>
    <version>version-one</version>

    <parent>
        <groupId>group-one</groupId>
        <artifactId>parent</artifactId>
        <version>version-one</version>
    </parent>
</project>
"""
        and:
        parseContext.getMetaDataArtifact({ it.name == 'parent' }, MavenPomArtifact) >> { new DefaultLocallyAvailableExternalResource(parent.toURI().toURL().toString(), new DefaultLocallyAvailableResource(parent)) }
        parseContext.getMetaDataArtifact({ it.name == 'grandparent' }, MavenPomArtifact) >> { new DefaultLocallyAvailableExternalResource(grandParent.toURI().toURL().toString(), new DefaultLocallyAvailableResource(grandParent)) }

        when:
        def descriptor = parsePom()

        then:
        descriptor.dependencies.length == 1
        def dep = descriptor.dependencies.first()
        dep.dependencyRevisionId == moduleId('group-two', 'artifact-two', '1.3')
        dep.moduleConfigurations == ['compile', 'runtime']
        hasDefaultDependencyArtifact(dep)
    }

    def "uses dependency management section from imported POM in active profile to define defaults for profile dependency"() {
        given:
        def imported = tmpDir.file("imported.xml") << """
<project>
    <modelVersion>4.0.0</modelVersion>
    <groupId>different-group</groupId>
    <artifactId>imported</artifactId>
    <version>different-version</version>

    <dependencyManagement>
        <dependencies>
            <dependency>
                <groupId>group-two</groupId>
                <artifactId>artifact-two</artifactId>
                <version>1.2</version>
            </dependency>
        </dependencies>
    </dependencyManagement>
</project>
"""

        pomFile << """
<project>
    <modelVersion>4.0.0</modelVersion>
    <groupId>group-one</groupId>
    <artifactId>artifact-one</artifactId>
    <version>version-one</version>

    <profiles>
        <profile>
            <id>profile-1</id>
            <activation>
                <activeByDefault>true</activeByDefault>
            </activation>
            <dependencyManagement>
                <dependencies>
                    <dependency>
                        <groupId>different-group</groupId>
                        <artifactId>imported</artifactId>
                        <version>different-version</version>
                        <type>pom</type>
                        <scope>import</scope>
                    </dependency>
                </dependencies>
            </dependencyManagement>
            <dependencies>
                <dependency>
                    <groupId>group-two</groupId>
                    <artifactId>artifact-two</artifactId>
                </dependency>
            </dependencies>
        </profile>
    </profiles>
</project>
"""
        and:
        parseContext.getMetaDataArtifact({ it.name == 'imported' }, MavenPomArtifact) >> { new DefaultLocallyAvailableExternalResource(imported.toURI().toURL().toString(), new DefaultLocallyAvailableResource(imported)) }

        when:
        def descriptor = parsePom()

        then:
        descriptor.dependencies.length == 1
        def dep = descriptor.dependencies.first()
        dep.dependencyRevisionId == moduleId('group-two', 'artifact-two', '1.2')
        dep.moduleConfigurations == ['compile', 'runtime']
        hasDefaultDependencyArtifact(dep)
    }

    def "pom with project coordinates defined by profile activated by system property value"() {
        given:
        String customPropertyName = new UUIDGenerator().generateId()
        System.properties[customPropertyName] = 'BLUE'

        and:
        pomFile << """
<project>
    <modelVersion>4.0.0</modelVersion>
    <groupId>\${some.group}</groupId>
    <artifactId>\${some.artifact}</artifactId>
    <version>\${some.version}</version>

    <profiles>
        <profile>
            <id>profile-1</id>
            <activation>
                <property>
                    <name>${customPropertyName}</name>
                    <value>BLUE</value>
                </property>
            </activation>
            <properties>
                <some.group>group-one</some.group>
                <some.artifact>artifact-one</some.artifact>
                <some.version>version-one</some.version>
            </properties>
        </profile>
    </profiles>
</project>
"""

        when:
        def descriptor = parsePom()

        then:
        descriptor.moduleRevisionId == moduleId('group-one', 'artifact-one', 'version-one')
        descriptor.dependencies.length == 0

        cleanup:
        System.clearProperty(customPropertyName)
    }

    def "pom with dependency coordinates defined by profile activated by system property value"() {
        given:
        String customPropertyName = new UUIDGenerator().generateId()
        System.properties[customPropertyName] = 'BLUE'

        and:
        pomFile << """
<project>
    <modelVersion>4.0.0</modelVersion>
    <groupId>group-one</groupId>
    <artifactId>artifact-one</artifactId>
    <version>version-one</version>

    <dependencies>
        <dependency>
            <groupId>\${some.group}</groupId>
            <artifactId>\${some.artifact}</artifactId>
            <version>\${some.version}</version>
        </dependency>
    </dependencies>

    <profiles>
        <profile>
            <id>profile-1</id>
            <activation>
                <property>
                    <name>${customPropertyName}</name>
                    <value>BLUE</value>
                </property>
            </activation>
            <properties>
                <some.group>group-two</some.group>
                <some.artifact>artifact-two</some.artifact>
                <some.version>version-two</some.version>
            </properties>
        </profile>
    </profiles>
</project>
"""

        when:
        def descriptor = parsePom()

        then:
        descriptor.moduleRevisionId == moduleId('group-one', 'artifact-one', 'version-one')
        descriptor.dependencies.length == 1
        descriptor.dependencies.first().dependencyRevisionId == moduleId('group-two', 'artifact-two', 'version-two')
        hasDefaultDependencyArtifact(descriptor.dependencies.first())

        cleanup:
        System.clearProperty(customPropertyName)
    }

    def "uses parent properties from activated by system property value to provide default values for a dependency"() {
        given:
        String customPropertyName = new UUIDGenerator().generateId()
        System.properties[customPropertyName] = 'BLUE'

        and:
        def parent = tmpDir.file("parent.xml") << """
<project>
    <modelVersion>4.0.0</modelVersion>
    <groupId>group-one</groupId>
    <artifactId>artifact-one</artifactId>
    <version>version-one</version>

    <profiles>
        <profile>
            <id>profile-1</id>
            <activation>
                <property>
                    <name>${customPropertyName}</name>
                    <value>BLUE</value>
                </property>
            </activation>
            <properties>
                <some.group>group-two</some.group>
                <some.artifact>artifact-two</some.artifact>
                <some.version>version-two</some.version>
            </properties>
        </profile>
    </profiles>
</project>
"""

        pomFile << """
<project>
    <modelVersion>4.0.0</modelVersion>
    <groupId>group-one</groupId>
    <artifactId>artifact-one</artifactId>
    <version>version-one</version>

    <parent>
        <groupId>group-one</groupId>
        <artifactId>parent</artifactId>
        <version>version-one</version>
    </parent>

    <dependencies>
        <dependency>
            <groupId>\${some.group}</groupId>
            <artifactId>\${some.artifact}</artifactId>
            <version>\${some.version}</version>
        </dependency>
    </dependencies>
</project>
"""
        and:
        parseContext.getMetaDataArtifact(_, MavenPomArtifact.class) >> { new DefaultLocallyAvailableExternalResource(parent.toURI().toURL().toString(), new DefaultLocallyAvailableResource(parent)) }

        when:
        def descriptor = parsePom()

        then:
        descriptor.dependencies.length == 1
        def dep = descriptor.dependencies.first()
        dep.dependencyRevisionId == moduleId('group-two', 'artifact-two', 'version-two')
        dep.moduleConfigurations == ['compile', 'runtime']
        hasDefaultDependencyArtifact(dep)

        cleanup:
        System.clearProperty(customPropertyName)
    }

    def "uses grand parent properties from profile activated by system property value to provide default values for a dependency"() {
        given:
        String customPropertyName = new UUIDGenerator().generateId()
        System.properties[customPropertyName] = 'BLUE'

        and:
        def grandParent = tmpDir.file("grandparent.xml") << """
<project>
    <groupId>different-group</groupId>
    <artifactId>grandparent</artifactId>
    <version>different-version</version>

    <profiles>
        <profile>
            <id>profile-1</id>
            <activation>
                <property>
                    <name>${customPropertyName}</name>
                    <value>BLUE</value>
                </property>
            </activation>
            <properties>
                <some.group>group-two</some.group>
                <some.artifact>artifact-two</some.artifact>
                <some.version>version-two</some.version>
            </properties>
        </profile>
    </profiles>
</project>
"""

        def parent = tmpDir.file("parent.xml") << """
<project>
    <modelVersion>4.0.0</modelVersion>
    <groupId>group-one</groupId>
    <artifactId>artifact-one</artifactId>
    <version>version-one</version>

    <parent>
        <groupId>different-group</groupId>
        <artifactId>grandparent</artifactId>
        <version>different-version</version>
    </parent>
</project>
"""

        pomFile << """
<project>
    <modelVersion>4.0.0</modelVersion>
    <groupId>group-one</groupId>
    <artifactId>artifact-one</artifactId>
    <version>version-one</version>

    <parent>
        <groupId>group-one</groupId>
        <artifactId>parent</artifactId>
        <version>version-one</version>
    </parent>

    <dependencies>
        <dependency>
            <groupId>\${some.group}</groupId>
            <artifactId>\${some.artifact}</artifactId>
            <version>\${some.version}</version>
        </dependency>
    </dependencies>
</project>
"""
        and:
        parseContext.getMetaDataArtifact({ it.name == 'parent' }, MavenPomArtifact) >> { new DefaultLocallyAvailableExternalResource(parent.toURI().toURL().toString(), new DefaultLocallyAvailableResource(parent)) }
        parseContext.getMetaDataArtifact({ it.name == 'grandparent' }, MavenPomArtifact) >> { new DefaultLocallyAvailableExternalResource(grandParent.toURI().toURL().toString(), new DefaultLocallyAvailableResource(grandParent)) }

        when:
        def descriptor = parsePom()

        then:
        descriptor.dependencies.length == 1
        def dep = descriptor.dependencies.first()
        dep.dependencyRevisionId == moduleId('group-two', 'artifact-two', 'version-two')
        dep.moduleConfigurations == ['compile', 'runtime']
        hasDefaultDependencyArtifact(dep)

        cleanup:
        System.clearProperty(customPropertyName)
    }

    def "uses dependency management section in profile activated by system property value to provide default values for a dependency"() {
        given:
        String customPropertyName = new UUIDGenerator().generateId()
        System.properties[customPropertyName] = 'BLUE'

        and:
        pomFile << """
<project>
    <modelVersion>4.0.0</modelVersion>
    <groupId>group-one</groupId>
    <artifactId>artifact-one</artifactId>
    <version>version-one</version>

    <dependencies>
        <dependency>
            <groupId>group-two</groupId>
            <artifactId>artifact-two</artifactId>
        </dependency>
    </dependencies>

    <profiles>
        <profile>
            <id>profile-1</id>
            <activation>
                <property>
                    <name>${customPropertyName}</name>
                    <value>BLUE</value>
                </property>
            </activation>
            <dependencyManagement>
                <dependencies>
                    <dependency>
                        <groupId>group-two</groupId>
                        <artifactId>artifact-two</artifactId>
                        <version>1.2</version>
                        <scope>test</scope>
                        <exclusions>
                            <exclusion>
                                <groupId>group-three</groupId>
                                <artifactId>artifact-three</artifactId>
                            </exclusion>
                        </exclusions>
                    </dependency>
                </dependencies>
            </dependencyManagement>
        </profile>
    </profiles>
</project>
"""

        when:
        def descriptor = parsePom()

        then:
        descriptor.dependencies.length == 1
        def dep = descriptor.dependencies.first()
        dep.dependencyRevisionId == moduleId('group-two', 'artifact-two', '1.2')
        dep.moduleConfigurations == ['test']
        dep.allExcludeRules.length == 1
        dep.allExcludeRules.first().id.moduleId == createModuleId('group-three', 'artifact-three')
        hasDefaultDependencyArtifact(dep)

        cleanup:
        System.clearProperty(customPropertyName)
    }

    def "uses parent pom dependency management section in profile activated by system property value to provide default values for a dependency"() {
        given:
        String customPropertyName = new UUIDGenerator().generateId()
        System.properties[customPropertyName] = 'BLUE'

        and:
        def parent = tmpDir.file("parent.xml") << """
<project>
    <modelVersion>4.0.0</modelVersion>
    <groupId>group-one</groupId>
    <artifactId>artifact-one</artifactId>
    <version>version-one</version>

    <profiles>
        <profile>
            <id>profile-1</id>
            <activation>
                <property>
                    <name>${customPropertyName}</name>
                    <value>BLUE</value>
                </property>
            </activation>
            <dependencyManagement>
                <dependencies>
                    <dependency>
                        <groupId>group-two</groupId>
                        <artifactId>artifact-two</artifactId>
                        <version>1.2</version>
                    </dependency>
                </dependencies>
            </dependencyManagement>
        </profile>
    </profiles>
</project>
"""

        pomFile << """
<project>
    <modelVersion>4.0.0</modelVersion>
    <groupId>group-one</groupId>
    <artifactId>artifact-one</artifactId>
    <version>version-one</version>

    <parent>
        <groupId>group-one</groupId>
        <artifactId>parent</artifactId>
        <version>version-one</version>
    </parent>

    <dependencies>
        <dependency>
            <groupId>group-two</groupId>
            <artifactId>artifact-two</artifactId>
        </dependency>
    </dependencies>
</project>
"""
        and:
        parseContext.getMetaDataArtifact(_, MavenPomArtifact) >> { new DefaultLocallyAvailableExternalResource(parent.toURI().toURL().toString(), new DefaultLocallyAvailableResource(parent)) }

        when:
        def descriptor = parsePom()

        then:
        descriptor.dependencies.length == 1
        def dep = descriptor.dependencies.first()
        dep.dependencyRevisionId == moduleId('group-two', 'artifact-two', '1.2')
        dep.moduleConfigurations == ['compile', 'runtime']
        hasDefaultDependencyArtifact(dep)

        cleanup:
        System.clearProperty(customPropertyName)
    }

    def "uses grand parent pom dependency management section in profile activated by system property value to provide default values for a dependency"() {
        given:
        String customPropertyName = new UUIDGenerator().generateId()
        System.properties[customPropertyName] = 'BLUE'

        and:
        def grandParent = tmpDir.file("grandparent.xml") << """
<project>
    <groupId>different-group</groupId>
    <artifactId>grandparent</artifactId>
    <version>different-version</version>

    <profiles>
        <profile>
            <id>profile-1</id>
            <activation>
                <property>
                    <name>${customPropertyName}</name>
                    <value>BLUE</value>
                </property>
            </activation>
            <dependencyManagement>
                <dependencies>
                    <dependency>
                        <groupId>group-two</groupId>
                        <artifactId>artifact-two</artifactId>
                        <version>1.2</version>
                    </dependency>
                </dependencies>
            </dependencyManagement>
        </profile>
    </profiles>
</project>
"""

        def parent = tmpDir.file("parent.xml") << """
<project>
    <modelVersion>4.0.0</modelVersion>
    <groupId>group-one</groupId>
    <artifactId>artifact-one</artifactId>
    <version>version-one</version>

    <parent>
        <groupId>different-group</groupId>
        <artifactId>grandparent</artifactId>
        <version>different-version</version>
    </parent>
</project>
"""

        pomFile << """
<project>
    <modelVersion>4.0.0</modelVersion>
    <groupId>group-one</groupId>
    <artifactId>artifact-one</artifactId>
    <version>version-one</version>

    <parent>
        <groupId>group-one</groupId>
        <artifactId>parent</artifactId>
        <version>version-one</version>
    </parent>

    <dependencies>
        <dependency>
            <groupId>group-two</groupId>
            <artifactId>artifact-two</artifactId>
        </dependency>
    </dependencies>
</project>
"""
        and:
        parseContext.getMetaDataArtifact({ it.name == 'parent' }, MavenPomArtifact) >> { new DefaultLocallyAvailableExternalResource(parent.toURI().toURL().toString(), new DefaultLocallyAvailableResource(parent)) }
        parseContext.getMetaDataArtifact({ it.name == 'grandparent' }, MavenPomArtifact) >> { new DefaultLocallyAvailableExternalResource(grandParent.toURI().toURL().toString(), new DefaultLocallyAvailableResource(grandParent)) }

        when:
        def descriptor = parsePom()

        then:
        descriptor.dependencies.length == 1
        def dep = descriptor.dependencies.first()
        dep.dependencyRevisionId == moduleId('group-two', 'artifact-two', '1.2')
        dep.moduleConfigurations == ['compile', 'runtime']
        hasDefaultDependencyArtifact(dep)

        cleanup:
        System.clearProperty(customPropertyName)
    }

    def "uses dependency management section from imported POM in profile activated by system property value to define defaults for main POM body dependency"() {
        given:
        String customPropertyName = new UUIDGenerator().generateId()
        System.properties[customPropertyName] = 'BLUE'

        and:
        def imported = tmpDir.file("imported.xml") << """
<project>
    <modelVersion>4.0.0</modelVersion>
    <groupId>different-group</groupId>
    <artifactId>imported</artifactId>
    <version>different-version</version>

    <dependencyManagement>
        <dependencies>
            <dependency>
                <groupId>group-two</groupId>
                <artifactId>artifact-two</artifactId>
                <version>1.2</version>
            </dependency>
        </dependencies>
    </dependencyManagement>
</project>
"""

        pomFile << """
<project>
    <modelVersion>4.0.0</modelVersion>
    <groupId>group-one</groupId>
    <artifactId>artifact-one</artifactId>
    <version>version-one</version>

    <dependencies>
        <dependency>
            <groupId>group-two</groupId>
            <artifactId>artifact-two</artifactId>
        </dependency>
    </dependencies>

    <profiles>
        <profile>
            <id>profile-1</id>
            <activation>
                <property>
                    <name>${customPropertyName}</name>
                    <value>BLUE</value>
                </property>
            </activation>
            <dependencyManagement>
                <dependencies>
                    <dependency>
                        <groupId>different-group</groupId>
                        <artifactId>imported</artifactId>
                        <version>different-version</version>
                        <type>pom</type>
                        <scope>import</scope>
                    </dependency>
                </dependencies>
            </dependencyManagement>
        </profile>
    </profiles>
</project>
"""
        and:
        parseContext.getMetaDataArtifact({ it.name == 'imported' }, MavenPomArtifact) >> { new DefaultLocallyAvailableExternalResource(imported.toURI().toURL().toString(), new DefaultLocallyAvailableResource(imported)) }

        when:
        def descriptor = parsePom()

        then:
        descriptor.dependencies.length == 1
        def dep = descriptor.dependencies.first()
        dep.dependencyRevisionId == moduleId('group-two', 'artifact-two', '1.2')
        dep.moduleConfigurations == ['compile', 'runtime']
        hasDefaultDependencyArtifact(dep)

        cleanup:
        System.clearProperty(customPropertyName)
    }

    def "pom with dependency defined in profile activated by system property value"() {
        given:
        String customPropertyName = new UUIDGenerator().generateId()
        System.properties[customPropertyName] = 'BLUE'

        and:
        pomFile << """
<project>
    <modelVersion>4.0.0</modelVersion>
    <groupId>group-one</groupId>
    <artifactId>artifact-one</artifactId>
    <version>version-one</version>

    <profiles>
        <profile>
            <id>profile-1</id>
            <activation>
                <property>
                    <name>${customPropertyName}</name>
                    <value>BLUE</value>
                </property>
            </activation>
            <dependencies>
                <dependency>
                    <groupId>group-two</groupId>
                    <artifactId>artifact-two</artifactId>
                    <version>version-two</version>
                </dependency>
            </dependencies>
        </profile>
    </profiles>
</project>
"""

        when:
        def descriptor = parsePom()

        then:
        descriptor.moduleRevisionId == moduleId('group-one', 'artifact-one', 'version-one')
        descriptor.dependencies.length == 1
        descriptor.dependencies.first().dependencyRevisionId == moduleId('group-two', 'artifact-two', 'version-two')
        hasDefaultDependencyArtifact(descriptor.dependencies.first())

        cleanup:
        System.clearProperty(customPropertyName)
    }

    def "uses parent dependency from profile activated by system property value"() {
        given:
        String customPropertyName = new UUIDGenerator().generateId()
        System.properties[customPropertyName] = 'BLUE'

        and:
        def parent = tmpDir.file("parent.xlm") << """
<project>
    <modelVersion>4.0.0</modelVersion>
    <groupId>group-one</groupId>
    <artifactId>artifact-one</artifactId>
    <version>version-one</version>

    <profiles>
        <profile>
            <id>profile-1</id>
            <activation>
                <property>
                    <name>${customPropertyName}</name>
                    <value>BLUE</value>
                </property>
            </activation>
            <dependencies>
                <dependency>
                    <groupId>group-two</groupId>
                    <artifactId>artifact-two</artifactId>
                    <version>version-two</version>
                </dependency>
            </dependencies>
        </profile>
    </profiles>
</project>
"""

        pomFile << """
<project>
    <modelVersion>4.0.0</modelVersion>
    <groupId>group-one</groupId>
    <artifactId>artifact-one</artifactId>
    <version>version-one</version>

    <parent>
        <groupId>group-one</groupId>
        <artifactId>parent</artifactId>
        <version>version-one</version>
    </parent>
</project>
"""
        and:
        parseContext.getMetaDataArtifact(_, MavenPomArtifact) >> { new DefaultLocallyAvailableExternalResource(parent.toURI().toURL().toString(), new DefaultLocallyAvailableResource(parent)) }

        when:
        def descriptor = parsePom()

        then:
        descriptor.dependencies.length == 1
        def dep = descriptor.dependencies.first()
        dep.dependencyRevisionId == moduleId('group-two', 'artifact-two', 'version-two')
        dep.moduleConfigurations == ['compile', 'runtime']
        hasDefaultDependencyArtifact(dep)

        cleanup:
        System.clearProperty(customPropertyName)
    }

    def "uses grand parent dependency from profile activated by system property value"() {
        given:
        String customPropertyName = new UUIDGenerator().generateId()
        System.properties[customPropertyName] = 'BLUE'

        and:
        def grandParent = tmpDir.file("grandparent.xml") << """
<project>
    <groupId>different-group</groupId>
    <artifactId>grandparent</artifactId>
    <version>different-version</version>

    <profiles>
        <profile>
            <id>profile-1</id>
            <activation>
                <property>
                    <name>${customPropertyName}</name>
                    <value>BLUE</value>
                </property>
            </activation>
            <dependencies>
                <dependency>
                    <groupId>group-two</groupId>
                    <artifactId>artifact-two</artifactId>
                    <version>1.2</version>
                </dependency>
            </dependencies>
        </profile>
    </profiles>
</project>
"""

        def parent = tmpDir.file("parent.xml") << """
<project>
    <modelVersion>4.0.0</modelVersion>
    <groupId>group-one</groupId>
    <artifactId>artifact-one</artifactId>
    <version>version-one</version>

    <parent>
        <groupId>different-group</groupId>
        <artifactId>grandparent</artifactId>
        <version>different-version</version>
    </parent>
</project>
"""

        pomFile << """
<project>
    <modelVersion>4.0.0</modelVersion>
    <groupId>group-one</groupId>
    <artifactId>artifact-one</artifactId>
    <version>version-one</version>

    <parent>
        <groupId>group-one</groupId>
        <artifactId>parent</artifactId>
        <version>version-one</version>
    </parent>
</project>
"""
        and:
        parseContext.getMetaDataArtifact({ it.name == 'parent' }, MavenPomArtifact) >> { new DefaultLocallyAvailableExternalResource(parent.toURI().toURL().toString(), new DefaultLocallyAvailableResource(parent)) }
        parseContext.getMetaDataArtifact({ it.name == 'grandparent' }, MavenPomArtifact) >> { new DefaultLocallyAvailableExternalResource(grandParent.toURI().toURL().toString(), new DefaultLocallyAvailableResource(grandParent)) }

        when:
        def descriptor = parsePom()

        then:
        descriptor.dependencies.length == 1
        def dep = descriptor.dependencies.first()
        dep.dependencyRevisionId == moduleId('group-two', 'artifact-two', '1.2')
        dep.moduleConfigurations == ['compile', 'runtime']
        hasDefaultDependencyArtifact(dep)

        cleanup:
        System.clearProperty(customPropertyName)
    }

    def "uses parent dependency over grand parent dependency with same groupId and artifactId from profile activated by system property value"() {
        given:
        String customPropertyName = new UUIDGenerator().generateId()
        System.properties[customPropertyName] = 'BLUE'

        and:
        def grandParent = tmpDir.file("grandparent.xml") << """
<project>
    <groupId>different-group</groupId>
    <artifactId>grandparent</artifactId>
    <version>different-version</version>

    <profiles>
        <profile>
            <id>profile-1</id>
            <activation>
                <property>
                    <name>${customPropertyName}</name>
                    <value>BLUE</value>
                </property>
            </activation>
            <dependencies>
                <dependency>
                    <groupId>group-two</groupId>
                    <artifactId>artifact-two</artifactId>
                    <version>1.2</version>
                </dependency>
            </dependencies>
        </profile>
    </profiles>
</project>
"""

        def parent = tmpDir.file("parent.xml") << """
<project>
    <modelVersion>4.0.0</modelVersion>
    <groupId>group-one</groupId>
    <artifactId>artifact-one</artifactId>
    <version>version-one</version>

    <parent>
        <groupId>different-group</groupId>
        <artifactId>grandparent</artifactId>
        <version>different-version</version>
    </parent>

    <profiles>
        <profile>
            <id>profile-2</id>
            <activation>
                <activeByDefault>true</activeByDefault>
            </activation>
            <dependencies>
                <dependency>
                    <groupId>group-two</groupId>
                    <artifactId>artifact-two</artifactId>
                    <version>1.3</version>
                </dependency>
            </dependencies>
        </profile>
    </profiles>
</project>
"""

        pomFile << """
<project>
    <modelVersion>4.0.0</modelVersion>
    <groupId>group-one</groupId>
    <artifactId>artifact-one</artifactId>
    <version>version-one</version>

    <parent>
        <groupId>group-one</groupId>
        <artifactId>parent</artifactId>
        <version>version-one</version>
    </parent>
</project>
"""
        and:
        parseContext.getMetaDataArtifact({ it.name == 'parent' }, MavenPomArtifact) >> { new DefaultLocallyAvailableExternalResource(parent.toURI().toURL().toString(), new DefaultLocallyAvailableResource(parent)) }
        parseContext.getMetaDataArtifact({ it.name == 'grandparent' }, MavenPomArtifact) >> { new DefaultLocallyAvailableExternalResource(grandParent.toURI().toURL().toString(), new DefaultLocallyAvailableResource(grandParent)) }

        when:
        def descriptor = parsePom()

        then:
        descriptor.dependencies.length == 1
        def dep = descriptor.dependencies.first()
        dep.dependencyRevisionId == moduleId('group-two', 'artifact-two', '1.3')
        dep.moduleConfigurations == ['compile', 'runtime']
        hasDefaultDependencyArtifact(dep)

        cleanup:
        System.clearProperty(customPropertyName)
    }

    def "uses dependency management section from imported POM in profile activated by system property value to define defaults for profile dependency"() {
        given:
        String customPropertyName = new UUIDGenerator().generateId()
        System.properties[customPropertyName] = 'BLUE'

        and:
        def imported = tmpDir.file("imported.xml") << """
<project>
    <modelVersion>4.0.0</modelVersion>
    <groupId>different-group</groupId>
    <artifactId>imported</artifactId>
    <version>different-version</version>

    <dependencyManagement>
        <dependencies>
            <dependency>
                <groupId>group-two</groupId>
                <artifactId>artifact-two</artifactId>
                <version>1.2</version>
            </dependency>
        </dependencies>
    </dependencyManagement>
</project>
"""

        pomFile << """
<project>
    <modelVersion>4.0.0</modelVersion>
    <groupId>group-one</groupId>
    <artifactId>artifact-one</artifactId>
    <version>version-one</version>

    <profiles>
        <profile>
            <id>profile-1</id>
            <activation>
                <property>
                    <name>${customPropertyName}</name>
                    <value>BLUE</value>
                </property>
            </activation>
            <dependencyManagement>
                <dependencies>
                    <dependency>
                        <groupId>different-group</groupId>
                        <artifactId>imported</artifactId>
                        <version>different-version</version>
                        <type>pom</type>
                        <scope>import</scope>
                    </dependency>
                </dependencies>
            </dependencyManagement>
            <dependencies>
                <dependency>
                    <groupId>group-two</groupId>
                    <artifactId>artifact-two</artifactId>
                </dependency>
            </dependencies>
        </profile>
    </profiles>
</project>
"""
        and:
        parseContext.getMetaDataArtifact({ it.name == 'imported' }, MavenPomArtifact) >> { new DefaultLocallyAvailableExternalResource(imported.toURI().toURL().toString(), new DefaultLocallyAvailableResource(imported)) }

        when:
        def descriptor = parsePom()

        then:
        descriptor.dependencies.length == 1
        def dep = descriptor.dependencies.first()
        dep.dependencyRevisionId == moduleId('group-two', 'artifact-two', '1.2')
        dep.moduleConfigurations == ['compile', 'runtime']
        hasDefaultDependencyArtifact(dep)

        cleanup:
        System.clearProperty(customPropertyName)
    }
}

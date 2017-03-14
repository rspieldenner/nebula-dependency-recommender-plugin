package netflix.nebula.dependency.recommender

import nebula.test.IntegrationSpec
import nebula.test.dependencies.DependencyGraphBuilder
import nebula.test.dependencies.GradleDependencyGenerator
import nebula.test.dependencies.maven.ArtifactType
import nebula.test.dependencies.maven.Pom
import nebula.test.dependencies.repositories.MavenRepo

class DependencyRecommendationsMultiprojectPluginSpec extends IntegrationSpec {
    def 'can use recommender across a multiproject'() {
        def depGraph = new DependencyGraphBuilder()
                .addModule('example:foo:1.0.0')
                .addModule('example:bar:1.0.0')
                .build()
        def generator = new GradleDependencyGenerator(depGraph)
        generator.generateTestMavenRepo()

        def a = addSubproject('a', '''\
                dependencies {
                    compile 'example:foo'
                }
            '''.stripIndent())
        writeHelloWorld('a', a)
        def b = addSubproject('b', '''\
                dependencies {
                    compile project(':a')
                }
            '''.stripIndent())
        writeHelloWorld('b', b)
        buildFile << """\
            allprojects {
                apply plugin: 'nebula.dependency-recommender'
                dependencyRecommendations {
                    map recommendations: ['example:foo': '1.0.0']
                }
            }
            subprojects {
                apply plugin: 'java'

                repositories {
                    ${generator.mavenRepositoryBlock}
                }
            }
            """.stripIndent()
        when:
        def results = runTasksSuccessfully(':a:dependencies', ':b:dependencies', 'build')

        then:
        noExceptionThrown()
        results.standardOutput.contains 'Recommending version 1.0.0 for dependency example:foo\n' +
                '\\--- project :a\n' +
                '     \\--- example:foo: -> 1.0.0'
    }

    def 'can use recommender with dependencyInsightEnhanced across a multiproject'() {
        def repo = new MavenRepo()
        repo.root = new File(projectDir, 'build/bomrepo')
        def pom = new Pom('test.nebula.bom', 'multiprojectbom', '1.0.0', ArtifactType.POM)
        pom.addManagementDependency('example', 'foo', '1.0.0')
        pom.addManagementDependency('example', 'bar', '1.0.0')
        repo.poms.add(pom)
        repo.generate()
        def depGraph = new DependencyGraphBuilder()
                .addModule('example:foo:1.0.0')
                .addModule('example:bar:1.0.0')
                .build()
        def generator = new GradleDependencyGenerator(depGraph)
        generator.generateTestMavenRepo()

        def a = addSubproject('a', '''\
                dependencies {
                    compile 'example:foo'
                }
            '''.stripIndent())
        writeHelloWorld('a', a)
        def b = addSubproject('b', '''\
                dependencies {
                    compile project(':a')
                }
            '''.stripIndent())
        writeHelloWorld('b', b)
        buildFile << """\
            allprojects {
                apply plugin: 'nebula.dependency-recommender'
            }
            subprojects {
                apply plugin: 'java'

                repositories {
                    maven { url '${repo.root.absolutePath}' }
                    ${generator.mavenRepositoryBlock}
                }

                dependencies {
                    nebulaRecommenderBom 'test.nebula.bom:multiprojectbom:1.0.0@pom'
                }
            }
            """.stripIndent()
        when:
        def results = runTasksSuccessfully(':a:dependencyInsightEnhanced', '--configuration', 'compile', '--dependency', 'foo')

        then:
        results.standardOutput.contains 'example:foo:1.0.0 (recommend 1.0.0 via conflict resolution recommendation)'
        results.standardOutput.contains 'nebula.dependency-recommender uses mavenBom: test.nebula.bom:multiprojectbom:pom:1.0.0'
    }
}

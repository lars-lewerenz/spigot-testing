package io.github.larslewerenz.spigottesting.gradle;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;
import org.gradle.work.DisableCachingByDefault;

/**
 * Copies a jar that ships inside this plugin (the test runtime, the bot controller) to the build
 * directory. Shipping them inside the plugin means projects only need the Gradle Plugin Portal
 * and Maven Central, no other repositories or credentials.
 */
@DisableCachingByDefault(because = "Copying a resource is faster than the build cache")
public abstract class ExtractBundledJar extends DefaultTask {

    static final String RUNTIME = "spigot-testing-runtime.jar";
    static final String BOTS = "spigot-testing-bots.jar";

    public ExtractBundledJar() {
        // Cheap, and a rebuilt plugin may ship a different jar under the same version.
        getOutputs().upToDateWhen(task -> false);
    }

    /** The resource's name next to this class in the plugin jar. */
    @Input
    public abstract Property<String> getResource();

    @OutputFile
    public abstract RegularFileProperty getJar();

    @TaskAction
    public void extract() throws IOException {
        String resource = getResource().get();
        try (InputStream in = ExtractBundledJar.class.getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalStateException(resource + " is missing from the spigot-testing plugin");
            }
            Files.copy(in, getJar().get().getAsFile().toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
    }
}

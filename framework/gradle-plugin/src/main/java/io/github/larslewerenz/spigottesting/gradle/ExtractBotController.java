package io.github.larslewerenz.spigottesting.gradle;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;

/**
 * Copies the bot controller jar that ships inside this plugin to the build directory, once per
 * build, before the server test tasks start their controllers from it.
 */
public abstract class ExtractBotController extends DefaultTask {

    static final String RESOURCE = "spigot-testing-bots.jar";

    public ExtractBotController() {
        // Cheap, and a rebuilt plugin may ship a different jar under the same version.
        getOutputs().upToDateWhen(task -> false);
    }

    @OutputFile
    public abstract RegularFileProperty getJar();

    @TaskAction
    public void extract() throws IOException {
        try (InputStream in = ExtractBotController.class.getResourceAsStream(RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException(RESOURCE + " is missing from the spigot-testing plugin");
            }
            Files.copy(in, getJar().get().getAsFile().toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
    }
}

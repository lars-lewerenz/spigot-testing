package io.github.larslewerenz.spigottesting.gradle;

import org.gradle.api.DefaultTask;
import org.gradle.work.DisableCachingByDefault;
import org.gradle.api.GradleException;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.TaskAction;

/**
 * Fails right away when the Minecraft EULA has not been accepted, before minutes go into building
 * or downloading servers that could not be started anyway.
 */
@DisableCachingByDefault(because = "Has no outputs")
public abstract class CheckEula extends DefaultTask {

    static final String MESSAGE = "Running a Minecraft server requires accepting the Minecraft EULA "
            + "(https://aka.ms/MinecraftEULA). If you accept it, set the Gradle property "
            + "spigotTesting.acceptEula=true (e.g. -PspigotTesting.acceptEula=true or in ~/.gradle/gradle.properties).";

    @Input
    public abstract Property<Boolean> getAcceptEula();

    @TaskAction
    public void check() {
        if (!getAcceptEula().get()) {
            throw new GradleException(MESSAGE);
        }
    }
}

package fixture;

public final class Fixture extends org.bukkit.plugin.java.JavaPlugin {

    @Override
    public void onEnable() {
        throw new IllegalStateException("boom on enable");
    }
}

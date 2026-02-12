package botamochi129.manual_enchance;

import java.time.Instant;

public interface BuildConfig {

    String MOD_VERSION = "@version@";

    int MOD_PROTOCOL_VERSION = @protocol_version@;

    Instant BUILD_TIME = Instant.ofEpochSecond(@build_time@);
}
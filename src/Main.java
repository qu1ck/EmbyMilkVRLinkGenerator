import com.beust.jcommander.IValueValidator;
import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.ParameterException;
import mediabrowser.apiinteraction.ApiClient;
import mediabrowser.apiinteraction.ApiEventListener;
import mediabrowser.apiinteraction.Response;
import mediabrowser.apiinteraction.android.GsonJsonSerializer;
import mediabrowser.apiinteraction.device.Device;
import mediabrowser.apiinteraction.device.IDevice;
import mediabrowser.apiinteraction.http.IAsyncHttpClient;
import mediabrowser.logging.ConsoleLogger;
import mediabrowser.model.dto.BaseItemDto;
import mediabrowser.model.logging.ILogger;
import mediabrowser.model.logging.NullLogger;
import mediabrowser.model.serialization.IJsonSerializer;
import mediabrowser.model.users.AuthenticationResult;

import java.io.UnsupportedEncodingException;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class Main {

    public static Set<String> milkVRVideoTypes = new HashSet<>(Arrays.asList(
            "_2dp", "_3dpv", "_3dph", "180x180", "180x101", "_mono360",
            "3dv", "_tb", "3dh", "_lr", "180x180_3dv", "180x180_3dh",
            "180x180_squished_3dh", "180x160_3dv", "180hemispheres",
            "cylinder_slice_2x25_3dv", "cylinder_slice_16x9_3dv",
            "sib3d", "_planetarium", "_fulldome", "_v360", "_rtxp"
    ));

    @Parameter(names = {"-h", "--help"}, help = true)
    private boolean help;

    @Parameter(names = {"-u", "--user"}, description = "Emby server username")
    private String username = "emby";

    @Parameter(names = {"-p", "--password"}, description = "Emby server user password")
    private String password = "";

    @Parameter(names = {"-s", "--server"}, description = "Emby server url")
    private final String serverAddress = "http://192.168.0.1:8096";

    @Parameter(
            names = {"-d", "--serverDirectory"},
            description = "Server library/directory/collection name regex")
    private String serverDir = ".*";

    @Parameter(
            names = {"-t", "--videoType"}, description = "MilkVR video type",
            validateValueWith = Main.VideoTypeValidator.class
    )
    private String videoType = "_2dp";

    @Parameter(names = {"-x", "--prefix"}, description = "Short prefix for generated mvrl files")
    private String filePrefix = "";

    @Parameter(names = {"-T", "--threads"}, description = "Number of network threads (set to 20-100 for slow connections)")
    private int threads = 5;

    @Parameter(names = {"-v", "--verbose"}, description = "Verbose output (prints network request/responses)")
    private boolean verbose = false;

    public static class VideoTypeValidator implements IValueValidator<String> {

        @Override
        public void validate(String name, String value) throws ParameterException {
            if (!milkVRVideoTypes.contains(value)) {
                throw new ParameterException("Video type should be one of: " + milkVRVideoTypes);
            }
        }
    }


    public static void main(String args[]) throws Exception {
        Main main = new Main();
        JCommander jc = new JCommander(main, args);
        if (main.help) {
            jc.usage();
            return;
        }
        main.run();
    }

    public void run() throws UnsupportedEncodingException, NoSuchAlgorithmException {
        java.util.logging.LogManager.getLogManager().reset();
        final ListingGenerator listingGenerator = new ListingGenerator(serverDir, videoType, filePrefix, threads);
        final ApiClient client = authenticate();

        client.GetRootFolderAsync(client.getCurrentUserId(), new Response<BaseItemDto>() {
            @Override
            public void onResponse(BaseItemDto response) {
                try {
                    listingGenerator.makeListing(client, response.getId());
                } catch (InterruptedException e) {
                    // ignore
                }
            }
        });

        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                if (!listingGenerator.isDone()) {
                    System.out.println("Interrupting generator");
                }
                listingGenerator.shutdown();
            }
        });

    }

    public ApiClient authenticate() throws UnsupportedEncodingException, NoSuchAlgorithmException {
        ILogger logger = new NullLogger();
        if (verbose) {
            logger = new ConsoleLogger();
        }

        IAsyncHttpClient httpClient = new SyncHttpClient(logger);
        IJsonSerializer jsonSerializer = new GsonJsonSerializer();
        IDevice device = new Device("MilkVR link generator app", "java");

        ApiClient apiClient = new ApiClient(httpClient, jsonSerializer, logger,
                serverAddress, "MilkVR link generator", "0.1", device, new ApiEventListener());


        final ILogger finalLogger = logger;
        apiClient.AuthenticateUserAsync(username, password, new Response<AuthenticationResult>(){

            @Override
            public void onResponse(AuthenticationResult result) {
                if (result.getServerId() != null) {
                    finalLogger.Info("Connected to emby server " + result.getServerId());
                }
            }
        });

        return apiClient;
    }
}

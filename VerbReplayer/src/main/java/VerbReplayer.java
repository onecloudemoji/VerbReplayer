import burp.api.montoya.BurpExtension;
import burp.api.montoya.MontoyaApi;
import burp.api.montoya.proxy.http.ProxyRequestHandler;
import burp.api.montoya.proxy.http.InterceptedRequest;
import burp.api.montoya.proxy.http.ProxyRequestToBeSentAction;
import burp.api.montoya.proxy.http.ProxyRequestReceivedAction;
import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.http.message.responses.HttpResponse;
import java.net.URL;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class VerbReplayer implements BurpExtension, ProxyRequestHandler {
    private static MontoyaApi api;
    private UserInterface userInterface;
    private final Set<String> excludedExtensions = new HashSet<>(Arrays.asList(
            ".jpg", ".jpeg", ".png", ".gif", ".bmp", ".svg", ".webp", ".ico",
            ".tiff", ".pdf", ".woff", ".woff2", ".ttf", ".eot", ".mp4", ".avi",
            ".mov", ".mp3", ".wav", ".zip", ".rar", ".7z", ".gz", ".tar"
    ));

    @Override
    public void initialize(MontoyaApi api) {
        VerbReplayer.api = api;
        api.extension().setName("VerbReplayer");
        api.logging().logToOutput("VERBREPLAYER HAS STARTED.");

        userInterface = new UserInterface(api);
        api.userInterface().registerSuiteTab("VerbReplayer", userInterface.getMainPanel());

        api.proxy().registerRequestHandler(this);
    }

    @Override
    public ProxyRequestToBeSentAction handleRequestToBeSent(InterceptedRequest interceptedRequest) {
        String urlString = interceptedRequest.url().toString().toLowerCase();

        for (String extension : excludedExtensions) {
            if (urlString.endsWith(extension)) {
                api.logging().logToOutput("Request skipped due to excluded extension: " + urlString);
                return ProxyRequestToBeSentAction.continueWith(interceptedRequest);
            }
        }

        try {
            URL url = new URL(urlString);
            String hostname = url.getHost();
            String uri = url.getPath();

            // Get allowed HTTP verbs from the History tab's checkboxes (which control replay).
            java.util.List<String> selectedVerbs = userInterface.getSelectedVerbs();

            for (String verb : selectedVerbs) {
                HttpRequest modifiedRequest = interceptedRequest.withMethod(verb);
                HttpResponse response = api.http().sendRequest(modifiedRequest).response();
                int statusCode = response.statusCode();
                // Log every replayed request; the UI will sort them into success/error lists.
                userInterface.logTraffic(
                        verb,
                        hostname + uri,
                        statusCode,
                        modifiedRequest,
                        response.toString()
                );
            }
        } catch (Exception e) {
            api.logging().logToError("Error parsing URL: " + e.getMessage());
        }

        return ProxyRequestToBeSentAction.continueWith(interceptedRequest);
    }

    @Override
    public ProxyRequestReceivedAction handleRequestReceived(InterceptedRequest interceptedRequest) {
        api.logging().logToOutput("Request Received Headers: " + interceptedRequest.headers());
        return ProxyRequestReceivedAction.continueWith(interceptedRequest);
    }

    public static void sendToRepeater(HttpRequest httpRequest) {
        api.repeater().sendToRepeater(httpRequest);
    }
}

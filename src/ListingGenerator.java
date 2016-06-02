import mediabrowser.apiinteraction.ApiClient;
import mediabrowser.apiinteraction.Response;
import mediabrowser.model.dto.BaseItemDto;
import mediabrowser.model.querying.ItemFields;
import mediabrowser.model.querying.ItemQuery;
import mediabrowser.model.querying.ItemsResult;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public class ListingGenerator {

    private final ExecutorService executor;

    private final String videoType;
    private final String filePrefix;
    private final Pattern serverDirPattern;
    private final AtomicInteger tasks = new AtomicInteger(0);
    private final AtomicInteger fileCount = new AtomicInteger(0);
    private final AtomicInteger folderCount = new AtomicInteger(0);
    private final CountDownLatch latch = new CountDownLatch(1);

    public ListingGenerator(String serverDir, String videoType, String filePrefix, int threads) {
        this.videoType = videoType;
        this.filePrefix = filePrefix;
        try {
            this.serverDirPattern = Pattern.compile(serverDir);
        } catch (PatternSyntaxException e) {
            System.out.println(videoType + " is invalid regular expression");
            throw e;
        }
        executor = Executors.newFixedThreadPool(threads);
    }

    public void makeListing(ApiClient client, String rootFolderId) throws InterruptedException {
        File vrlinksDir = new File("vrlinks");
        if (vrlinksDir.exists()) {
            deleteDir(vrlinksDir);
            Thread.sleep(10);
        }
        if (!vrlinksDir.mkdirs()) {
            throw new RuntimeException("Failed to created vrlinks dir");
        }
        doListing(client, false, rootFolderId, videoType);

        // wait for first task to run
        latch.await();
        while (tasks.get() > 0) {
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                System.out.println("Interrupted");
                throw e;
            }
        }
        shutdown();
        System.out.println(String.format("Done. Total %d folders scanned, %d files generated",
                folderCount.get(), fileCount.get()));
    }

    private void deleteDir(File dir) {
        if (dir.isDirectory()) {
            for (File f : dir.listFiles()) deleteDir(f);
        }
        dir.delete();
    }

    private void doListing(final ApiClient client, final boolean printItems, final String containerId, final String defaultVideoType) {
        ItemQuery query = new ItemQuery();
        query.setParentId(containerId);
        query.setUserId(client.getCurrentUserId());
        query.setFields(new ItemFields[]{ItemFields.Tags});
        client.GetItemsAsync(query, new Response<ItemsResult>() {
            @Override
            public void onResponse(final ItemsResult response) {
                tasks.incrementAndGet();
                folderCount.incrementAndGet();
                latch.countDown();
                executor.submit(() -> {
                    for (BaseItemDto item : response.getItems()) {
                        String videoType1 = defaultVideoType;
                        if (item.getTags() != null) {
                            for (String tag: item.getTags()) {
                                if (Main.milkVRVideoTypes.contains(tag)) {
                                    videoType1 = tag;
                                }
                            }
                        }
                        if (item.getIsFolder()) {
                            System.out.println("Checking folder " + item.getName());
                            doListing(client, serverDirPattern.matcher(item.getName()).matches(), item.getId(), videoType1);
                        } else if (item.getIsVideo() && printItems) {
                            writeVideoFile(client, item.getId(), videoType1);
                        }
                    }
                    tasks.decrementAndGet();
                });
            }

            @Override
            public void onError(Exception exception) {
                System.err.println("Error listing container " + containerId);
                System.err.println(exception);
                tasks.decrementAndGet();
            }
        });
    }

    private void writeVideoFile(final ApiClient client, final String id, final String videoType) {
        client.GetItemAsync(id, client.getCurrentUserId(), new Response<BaseItemDto>() {
            @Override
            public void onResponse(BaseItemDto item) {
                tasks.incrementAndGet();
                fileCount.incrementAndGet();
                executor.submit(() -> {
                    try {
                        System.out.println("Generating mvrl file for video '" + item.getName() + "'");
                        String filename = "vrlinks/" + (filePrefix.isEmpty() ? "" : (filePrefix + " ")) + item.getName() + ".mvrl";
                        PrintWriter fw = new PrintWriter(new FileWriter(filename));
                        String url = client.getApiUrl() + "/Videos/" + id + "/stream?static=true";
                        String imgurl = client.getApiUrl() + "/Items/" + id + "/images/Primary";
                        fw.println(url);
                        fw.println(videoType);
                        fw.println(); // audio type
                        fw.print(imgurl);
                        fw.close();
                    } catch (IOException e) {
                        System.err.println("Error generating mvrl file");
                        e.printStackTrace();
                    }
                    tasks.decrementAndGet();
                });
            }

            @Override
            public void onError(Exception exception) {
                System.err.println("Error fetching video info");
                System.err.println(exception);
                tasks.decrementAndGet();
            }
        });

    }

    public void shutdown() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                System.out.println("Executor still not shut down, trying force shutdown");
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            // ignore
        }
    }

    public boolean isDone() {
        return executor.isShutdown();
    }
}

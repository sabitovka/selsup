package io.sabitovka.selsup;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import lombok.Data;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public class CrptApi {
    private final TimeUnit timeUnit;
    private final int requestLimit;
    private int currentRequestsCount = 0;
    private long lastRequestTime = System.currentTimeMillis();

    private final HttpClient client;
    private final ObjectMapper objectMapper;

    public CrptApi(TimeUnit timeUnit, int requestLimit) {
        this.timeUnit = timeUnit;
        this.requestLimit = requestLimit;

        this.client = HttpClient.newBuilder().build();
        this.objectMapper = new ObjectMapper().setPropertyNamingStrategy(PropertyNamingStrategy.SNAKE_CASE);
    }

    /**
     * Проверяет, можно ли выполнить запрос в текущий момент времени.
     *
     * @return Если превышено количество возможных запросов в момент времени, возвращает false, иначе true
     */
    private synchronized boolean isRequestAllowed() {
        long currentTime = System.currentTimeMillis();

        // Если с момента предыдущего запроса прошло нужное количество времени, делаем возможность выполнять запросы
        if (currentTime - lastRequestTime > timeUnit.toMillis(1)) {
            lastRequestTime = currentTime;
            currentRequestsCount = 0;
        }

        // Если не превышен лимит запросов, возвращаем true
        if (currentRequestsCount < requestLimit) {
            currentRequestsCount++;
            return true;
        }

        return false;
    }

    /**
     * Ожидает, пока не появится возможность выполнить запрос.
     * Перед проверками делает паузу на 1/10 единицы времени
     */
    private synchronized void waitForRequest() {
        try {
            while(!isRequestAllowed()) {
                wait(timeUnit.toMillis(1) / 10);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Создает Builder для выполнения Rest API запроса
     *
     * @param endpoint конечная точка запроса
     * @return Builder для продолжения создания запроса
     */
    private HttpRequest.Builder createRestApiReqeustBuilder(String endpoint) {
        return HttpRequest.newBuilder()
                .uri(URI.create(Constants.API_V3_BASE_URL + endpoint));
                // Здесь можно дописать другие параметры билдера (заголовки, авторизацию и т.п.)
    }

    /**
     * Выполняет запрос на создание документа в личном кабинете в системе Честный знак
     * @param document Объект документа
     * @param signature Подпись документа
     */
    public void createDocument(Document document, String signature) {
        final String endpoint = "lk/documents/create";

        String documentJson;
        try {
            documentJson = objectMapper.writeValueAsString(document);
        } catch (JsonProcessingException e) {
            System.err.println("Не удалось преобразовать документ в формат JSON");
            throw new RuntimeException(e);
        }

        waitForRequest();

        HttpRequest request = createRestApiReqeustBuilder(endpoint)
                .POST(HttpRequest.BodyPublishers.ofString(documentJson))
                .build();

        // Выполняем запрос асинхронно
        CompletableFuture<HttpResponse<String>> creationAsyncResponse = client.sendAsync(request, HttpResponse.BodyHandlers.ofString());
        creationAsyncResponse.thenAccept(response -> {
            if (response.statusCode() == 200) {
                System.out.printf("Документ %s создан\n", signature);
                // Можно вызвать какую-нибудь функцию обратного вызова
            } else {
                System.out.printf("Документ %s не создан\n", signature);
            }
        });

        System.out.printf("[%s] Зарос на создание документа отправлен\n", signature);
    }

    public static void main(String[] args) throws IOException {
        CrptApi obj = new CrptApi(TimeUnit.SECONDS, 5);
        DocumentRepository documentRepository = new DocumentRepository();

        List<Document> documents = documentRepository.findDocumentsLimit(20);
        documents.forEach(document -> obj.createDocument(document, documentRepository.randomString(10)));

        System.in.read();
    }

    private static final class Constants {
        public static String API_V3_BASE_URL = "https://ismp.crpt.ru/api/v3/";
        private Constants() {};
    }

    private static class DocumentRepository {

        protected String randomString(int charsCount) {
            int leftLimit = 97;
            int rightLimit = 122;
            int targetStringLength = 10;
            Random random = new Random();

            return random.ints(leftLimit, rightLimit + 1)
                    .limit(targetStringLength)
                    .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append)
                    .toString();
        }

        private int randomInt(int min, int max) {
            return (int) ((Math.random() * (max - min)) + min);
        }

        private Document createRandomDocument() {
            Description description = new Description();
            description.setParticipantInn(randomString(8));

            int productsCount = randomInt(1, 5);
            List<Product> products = new ArrayList<>(productsCount);

            for (int i = 0; i < productsCount; i++) {
                Product product = new Product();
                product.setCertificateDocument(randomString(8));
                product.setCertificateDocumentDate("2020-01-23");
                product.setCertificateDocumentNumber(randomString(8));
                product.setOwnerInn(randomString(8));
                product.setProducerInn(randomString(8));
                product.setProductionDate("2020-01-23");
                product.setTnvedCode(randomString(8));
                product.setUitCode(randomString(8));
                product.setUituCode(randomString(8));

                products.add(product);
            }


            Document document = new Document();
            document.setDescription(description);
            document.setDocId(randomString(8));
            document.setDocStatus(randomString(8));
            document.setDocType("LP_INTRODUCE_GOODS");
            document.setImportRequest(true);
            document.setOwnerInn(randomString(8));
            document.setParticipantInn(randomString(8));
            document.setProducerInn(randomString(8));
            document.setProductionDate("2020-01-23");
            document.setProductionType(randomString(8));
            document.setProducts(products);
            document.setRegDate("2020-01-23");
            document.setRegNumber(randomString(8));

            return document;
        }

        public List<Document> findDocumentsLimit(int limit) {
            List<Document> documents = new ArrayList<>();
            for (int i = 0; i < limit; i++) {
                documents.add(createRandomDocument());
            }
            return documents;
        }
    }

    @Data
    private static class Document {
        private Description description;
        private String docId;
        private String docStatus;
        private String docType;
        private boolean importRequest;
        private String ownerInn;
        private String participantInn;
        private String producerInn;
        private String productionDate;
        private String productionType;
        private List<Product> products;
        private String regDate;
        private String regNumber;
    }

    @Data
    private static class Description {
        private String participantInn;
    }

    @Data
    private static class Product {
        private String certificateDocument;
        private String certificateDocumentDate;
        private String certificateDocumentNumber;
        private String ownerInn;
        private String producerInn;
        private String productionDate;
        private String tnvedCode;
        private String uitCode;
        private String uituCode;
    }
}

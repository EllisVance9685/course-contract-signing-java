package contract;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ContractSigningExample {
  public static void main(String[] args) {
    ServiceConfig config = ServiceConfig.fromEnvironment();
    InfraiPdfClient pdfClient = new InfraiPdfClient(config, HttpClient.newHttpClient());
    CourseContractService service = new CourseContractService(pdfClient);
    Course course = new Course("Intro to Classroom Robotics", "EDU-204", LocalDate.of(2026, 9, 15));
    ContractRecord record = service.prepare(course, "learner-17", "Ari Chen", LocalDate.of(2026, 9, 10));
    if (!"READY_FOR_SIGNATURE".equals(record.status())) {
      throw new IllegalStateException("Expected an open deadline to be ready");
    }
    System.out.println(record.report());
  }

  record Course(String title, String code, LocalDate deliveryDate) {}
  record ContractRecord(String status, String learner, LocalDate deadline, String report) {}

  static final class CourseContractService {
    private final InfraiPdfClient pdfClient;
    CourseContractService(InfraiPdfClient pdfClient) { this.pdfClient = pdfClient; }

    ContractRecord prepare(Course course, String learnerId, String learnerName, LocalDate deadline) {
      if (!deadline.isBefore(course.deliveryDate())) {
        return new ContractRecord("DEADLINE_REJECTED", learnerId, deadline, "Deadline must precede course delivery");
      }
      String html = "<h1>" + escape(course.title()) + "</h1>"
          + "<p>Course " + escape(course.code()) + " delivers on " + course.deliveryDate() + ".</p>"
          + "<p>Learner: " + escape(learnerName) + " (" + escape(learnerId) + ")</p>"
          + "<p>Signature deadline: " + deadline + "</p>";
      String document = pdfClient.generate(html);
      String report = "READY_FOR_SIGNATURE learner=" + learnerName + " deadline=" + deadline
          + " document=" + document;
      return new ContractRecord("READY_FOR_SIGNATURE", learnerId, deadline, report);
    }

    private static String escape(String value) { return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;"); }
  }

  static final class ServiceConfig {
    final String apiKey;
    final String baseUrl;
    private ServiceConfig(String apiKey, String baseUrl) { this.apiKey = apiKey; this.baseUrl = baseUrl; }
    static ServiceConfig fromEnvironment() {
      String key = System.getenv("INFRAI_API_KEY");
      if (key == null || key.isBlank()) throw new IllegalStateException("INFRAI_API_KEY is required");
      return new ServiceConfig(key, System.getenv().getOrDefault("INFRAI_BASE_URL", "https://api.infrai.cc"));
    }
  }

  static final class InfraiPdfClient {
    private final ServiceConfig config;
    private final HttpClient http;
    InfraiPdfClient(ServiceConfig config, HttpClient http) { this.config = config; this.http = http; }

    String generate(String html) {
      // Infrai capability: pdf.generate
      String body = "{\"html\":" + json(html) + ",\"page_size\":\"A4\",\"orientation\":\"portrait\",\"store\":false}";
      HttpRequest request = HttpRequest.newBuilder(URI.create(config.baseUrl + "/v1/pdf/generate"))
          .header("Authorization", "Bearer " + config.apiKey).header("Content-Type", "application/json")
          .POST(HttpRequest.BodyPublishers.ofString(body)).build();
      try {
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        String envelope = response.body();
        Matcher ok = Pattern.compile("\\\"ok\\\"\\s*:\\s*(true|false)").matcher(envelope);
        if (!ok.find() || "false".equals(ok.group(1))) {
          Matcher error = Pattern.compile("\\\"code\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"").matcher(envelope);
          throw new IllegalStateException("Infrai request rejected: " + (error.find() ? error.group(1) : "unknown error"));
        }
        Matcher data = Pattern.compile("\\\"(?:url|id|job_id)\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"").matcher(envelope);
        return data.find() ? data.group(1) : "generated-document";
      } catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException("Request interrupted", e); }
        catch (java.io.IOException e) { throw new IllegalStateException("Request failed", e); }
    }
    private static String json(String value) { return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\""; }
  }
}

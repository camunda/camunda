package io.camunda.common.http;

import com.google.common.reflect.TypeToken;
import io.camunda.common.auth.Authentication;
import io.camunda.common.auth.Product;
import io.camunda.common.exception.SdkException;
import io.camunda.common.json.JsonMapper;
import io.camunda.common.json.SdkObjectMapper;
import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.hc.client5.http.classic.methods.HttpDelete;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.http.message.BasicHeader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Default Http Client powered by Apache HttpClient */
public class DefaultHttpClient implements HttpClient {

  private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private String host = "";
  private String basePath = "";
  private final Map<Product, Map<Class<?>, String>> productMap;
  private final CloseableHttpClient httpClient;
  private final Authentication authentication;

  private final JsonMapper jsonMapper;

  public DefaultHttpClient(Authentication authentication) {
    this.authentication = authentication;
    this.httpClient = HttpClients.createDefault();
    this.jsonMapper = new SdkObjectMapper();
    this.productMap = new HashMap<>();
  }

  public DefaultHttpClient(
      Authentication authentication,
      CloseableHttpClient httpClient,
      JsonMapper jsonMapper,
      Map<Product, Map<Class<?>, String>> productMap) {
    this.authentication = authentication;
    this.httpClient = httpClient;
    this.jsonMapper = jsonMapper;
    this.productMap = productMap;
  }

  @Override
  public void init(String host, String basePath) {
    this.host = host;
    this.basePath = basePath;
  }

  @Override
  public void loadMap(Product product, Map<Class<?>, String> map) {
    this.productMap.put(product, map);
  }

  @Override
  public <T> T get(Class<T> responseType, Long key) {
    return get(responseType, String.valueOf(key));
  }

  @Override
  public <T> T get(Class<T> responseType, String id) {
    String url = host + basePath + retrievePath(responseType) + "/" + id;
    HttpGet httpGet = new HttpGet(url);
    httpGet.addHeader(retrieveToken(responseType));
    T resp;
    try (CloseableHttpResponse response = httpClient.execute(httpGet)) {
      resp = parseAndRetry(response, responseType);
    } catch (Exception e) {
      LOG.error(
          "Failed GET with responseType {}, id {} due to {}", responseType, id, e.getMessage());
      throw new SdkException(e);
    }

    return resp;
  }

  @Override
  public <T, V, W> T get(
      Class<T> responseType, Class<V> parameterType, TypeToken<W> selector, Long key) {
    return get(responseType, parameterType, selector, String.valueOf(key));
  }

  private <T, V, W> T get(
      Class<T> responseType, Class<V> parameterType, TypeToken<W> selector, String id) {
    String resourcePath = retrievePath(selector.getClass());
    if (resourcePath.contains("{key}")) {
      resourcePath = resourcePath.replace("{key}", String.valueOf(id));
    } else {
      resourcePath = resourcePath + "/" + id;
    }
    String url = host + basePath + resourcePath;
    HttpGet httpGet = new HttpGet(url);
    httpGet.addHeader(retrieveToken(selector.getClass()));
    T resp;
    try (CloseableHttpResponse response = httpClient.execute(httpGet)) {
      resp = parseAndRetry(response, responseType, parameterType, selector);
    } catch (Exception e) {
      LOG.error(
          "Failed GET with responseType {}, parameterType {}, selector {}, id {} due to {}",
          responseType,
          parameterType,
          selector,
          id,
          e.getMessage());
      throw new SdkException(e);
    }
    return resp;
  }

  @Override
  public <T> String getXml(Class<T> selector, Long key) {
    String url = host + basePath + retrievePath(selector) + "/" + key + "/xml";
    HttpGet httpGet = new HttpGet(url);
    httpGet.addHeader(retrieveToken(selector));
    String xml;
    try (CloseableHttpResponse response = httpClient.execute(httpGet)) {
      xml = parseXMLAndRetry(response, selector);
    } catch (Exception e) {
      LOG.error("Failed GET with selector {}, key {} due to {}", selector, key, e.getMessage());
      throw new SdkException(e);
    }
    return xml;
  }

  @Override
  public <T, V, W, U> T post(
      Class<T> responseType, Class<V> parameterType, TypeToken<W> selector, U body) {
    String url = host + basePath + retrievePath(selector.getClass());
    HttpPost httpPost = new HttpPost(url);
    httpPost.addHeader("Content-Type", "application/json");
    httpPost.addHeader(retrieveToken(selector.getClass()));
    T resp;
    String data = jsonMapper.toJson(body);
    httpPost.setEntity(new StringEntity(data));
    try (CloseableHttpResponse response = httpClient.execute(httpPost)) {
      resp = parseAndRetry(response, responseType, parameterType, selector);
    } catch (Exception e) {
      LOG.error(
          "Failed POST with responseType {}, parameterType {}, selector {}, body {} due to {}",
          responseType,
          parameterType,
          selector,
          body,
          e.getMessage());
      throw new SdkException(e);
    }
    return resp;
  }

  @Override
  public <T, V> T delete(Class<T> responseType, Class<V> selector, Long key) {
    String resourcePath = retrievePath(selector) + "/" + key;
    String url = host + basePath + resourcePath;
    HttpDelete httpDelete = new HttpDelete(url);
    httpDelete.addHeader(retrieveToken(selector));
    T resp = null;
    try (CloseableHttpResponse response = httpClient.execute(httpDelete)) {
      resp = parseAndRetry(response, responseType, selector);
    } catch (Exception e) {
      LOG.error(
          "Failed DELETE with responseType {}, selector {}, key {}, due to {}",
          responseType,
          selector,
          key,
          e.getMessage());
      throw new SdkException(e);
    }
    return resp;
  }

  private <T> String retrievePath(Class<T> clazz) {
    AtomicReference<String> path = new AtomicReference<>();
    productMap.forEach(
        (product, map) -> {
          if (map.containsKey(clazz)) {
            path.set(map.get(clazz));
          }
        });
    return path.get();
  }

  private <T> Header retrieveToken(Class<T> clazz) {
    AtomicReference<Product> currentProduct = new AtomicReference<>();
    productMap.forEach(
        (product, map) -> {
          if (map.containsKey(clazz)) {
            currentProduct.set(product);
          }
        });
    Map.Entry<String, String> header = authentication.getTokenHeader(currentProduct.get());
    return new BasicHeader(header.getKey(), header.getValue());
  }

  private <T> Product getProduct(Class<T> clazz) {
    AtomicReference<Product> currentProduct = new AtomicReference<>();
    productMap.forEach(
        (product, map) -> {
          if (map.containsKey(clazz)) {
            currentProduct.set(product);
          }
        });
    return currentProduct.get();
  }

  // TODO: Refactor duplicate code parseAndRetry()

  private <T> String parseXMLAndRetry(CloseableHttpResponse response, Class<T> selector)
      throws IOException {
    String resp;
    if (200 <= response.getCode() && response.getCode() <= 299) {
      resp =
          new String(
              Java8Utils.readAllBytes(response.getEntity().getContent()), StandardCharsets.UTF_8);
    } else {
      if (response.getCode() == HttpStatus.SC_UNAUTHORIZED
          || response.getCode() == HttpStatus.SC_FORBIDDEN) {
        authentication.resetToken(getProduct(selector.getClass()));
      }
      throw new SdkException("Response not successful: " + response.getCode());
    }
    return resp;
  }

  private <T> T parseAndRetry(CloseableHttpResponse response, Class<T> responseType)
      throws IOException {
    T resp;
    if (200 <= response.getCode() && response.getCode() <= 299) {
      HttpEntity entity = response.getEntity();
      String tmp = new String(Java8Utils.readAllBytes(entity.getContent()), StandardCharsets.UTF_8);
      resp = jsonMapper.fromJson(tmp, responseType);
      EntityUtils.consume(entity);
    } else {
      if (response.getCode() == HttpStatus.SC_UNAUTHORIZED
          || response.getCode() == HttpStatus.SC_FORBIDDEN) {
        authentication.resetToken(getProduct(responseType.getClass()));
        // TODO: Add capability to auto retry the existing request
      }
      throw new SdkException("Response not successful: " + response.getCode());
    }
    return resp;
  }

  private <T, V> T parseAndRetry(
      CloseableHttpResponse response, Class<T> responseType, Class<V> selector) throws IOException {
    T resp;
    if (200 <= response.getCode() && response.getCode() <= 299) {
      HttpEntity entity = response.getEntity();
      String tmp = new String(Java8Utils.readAllBytes(entity.getContent()), StandardCharsets.UTF_8);
      resp = jsonMapper.fromJson(tmp, responseType);
      EntityUtils.consume(entity);
    } else {
      if (response.getCode() == HttpStatus.SC_UNAUTHORIZED
          || response.getCode() == HttpStatus.SC_FORBIDDEN) {
        authentication.resetToken(getProduct(selector.getClass()));
      }
      throw new SdkException("Response not successful: " + response.getCode());
    }
    return resp;
  }

  private <T, V, W> T parseAndRetry(
      CloseableHttpResponse response,
      Class<T> responseType,
      Class<V> parameterType,
      TypeToken<W> selector)
      throws IOException {
    T resp;
    if (200 <= response.getCode() && response.getCode() <= 299) {
      HttpEntity entity = response.getEntity();
      String tmp = new String(Java8Utils.readAllBytes(entity.getContent()), StandardCharsets.UTF_8);
      resp = jsonMapper.fromJson(tmp, responseType, parameterType);
      EntityUtils.consume(entity);
    } else {
      if (response.getCode() == HttpStatus.SC_UNAUTHORIZED
          || response.getCode() == HttpStatus.SC_FORBIDDEN) {
        authentication.resetToken(getProduct(selector.getClass()));
      }
      throw new SdkException("Response not successful: " + response.getCode());
    }
    return resp;
  }
}

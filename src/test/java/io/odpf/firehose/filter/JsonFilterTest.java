package io.odpf.firehose.filter;

import io.odpf.firehose.config.KafkaConsumerConfig;
import io.odpf.firehose.config.enums.FilterDataSourceType;
import io.odpf.firehose.consumer.Message;
import io.odpf.firehose.consumer.TestBookingLogKey;
import io.odpf.firehose.consumer.TestBookingLogMessage;
import io.odpf.firehose.consumer.TestKey;
import io.odpf.firehose.consumer.TestMessage;
import io.odpf.firehose.metrics.Instrumentation;
import org.aeonbits.owner.ConfigFactory;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;

public class JsonFilterTest {

    private KafkaConsumerConfig kafkaConsumerConfig;

    private Filter filter;

    private TestMessage testMessageProto;
    private TestKey testKeyProto;
    private String testMessageJson;
    private String testKeyJson;


    @Mock
    private Instrumentation instrumentation;

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);
        Map<String, String> filterConfigs = new HashMap<>();

        filterConfigs.put("FILTER_JSON_DATA_SOURCE", "message");
        filterConfigs.put("FILTER_JSON_SCHEMA", "{\"properties\":{\"order_number\":{\"const\":123}}}");
        filterConfigs.put("FILTER_JSON_SCHEMA_PROTO_CLASS", TestMessage.class.getName());
        kafkaConsumerConfig = ConfigFactory.create(KafkaConsumerConfig.class, filterConfigs);

        testKeyProto = TestKey.newBuilder().setOrderNumber("123").setOrderUrl("abc").build();
        testMessageProto = TestMessage.newBuilder().setOrderNumber("123").setOrderUrl("abc").setOrderDetails("details").build();

        testKeyJson = "{\"order_number\":123,\"order_url\":\"abc\"}";
        testMessageJson = "{\"order_number\":123,\"order_url\":\"abc\",\"order_details\":\"details\"}";
    }

    @Test
    public void shouldFilterEsbMessages() throws FilterException {
        Message message = new Message(testKeyProto.toByteArray(), testMessageProto.toByteArray(), "topic1", 0, 100);
        filter = new JsonFilter(kafkaConsumerConfig, instrumentation);
        List<Message> filteredMessages = filter.filter(Arrays.asList(message));
        assertEquals(filteredMessages.get(0), message);
    }

    @Test
    public void shouldNotFilterEsbMessagesForEmptyBooleanValues() throws FilterException {
        TestBookingLogMessage bookingLogMessage = TestBookingLogMessage.newBuilder().setCustomerId("customerId").build();
        TestBookingLogKey bookingLogKey = TestBookingLogKey.newBuilder().build();
        Message message = new Message(bookingLogKey.toByteArray(), bookingLogMessage.toByteArray(), "topic1", 0, 100);
        HashMap<String, String> bookingFilterConfigs = new HashMap<>();

        bookingFilterConfigs.put("FILTER_JSON_DATA_SOURCE", "message");
        bookingFilterConfigs.put("FILTER_JSON_SCHEMA", "{\"properties\":{\"customer_dynamic_surge_enabled\":{\"const\":\"true\"}}}");
        bookingFilterConfigs.put("FILTER_JSON_SCHEMA_PROTO_CLASS", TestBookingLogMessage.class.getName());

        KafkaConsumerConfig bookingConsumerConfig = ConfigFactory.create(KafkaConsumerConfig.class, bookingFilterConfigs);
        JsonFilter bookingFilter = new JsonFilter(bookingConsumerConfig, instrumentation);
        List<Message> filteredMessages = bookingFilter.filter(Arrays.asList(message));
        assertEquals(filteredMessages.get(0), message);
    }

    @Test(expected = FilterException.class)
    public void shouldThrowExceptionOnInvalidFilterExpression() throws FilterException {
        Map<String, String> filterConfigs = new HashMap<>();
        filterConfigs.put("FILTER_JSON_DATA_SOURCE", "message");
        filterConfigs.put("FILTER_JSON_SCHEMA", "12/s");
        filterConfigs.put("FILTER_JSON_SCHEMA_PROTO_CLASS", TestMessage.class.getName());
        kafkaConsumerConfig = ConfigFactory.create(KafkaConsumerConfig.class, filterConfigs);

        filter = new JsonFilter(kafkaConsumerConfig, instrumentation);

        Message message = new Message(testKeyProto.toByteArray(), testMessageProto.toByteArray(), "topic1", 0, 100);
        filter.filter(Arrays.asList(message));
    }

    @Test
    public void shouldNotApplyFilterOnEmptyFilterType() throws FilterException {
        Map<String, String> filterConfigs = new HashMap<>();
        filterConfigs.put("FILTER_JSON_SCHEMA", "{\"properties\":{\"order_number\":{\"const\":123}}}");
        filterConfigs.put("FILTER_JSON_SCHEMA_PROTO_CLASS", TestMessage.class.getName());
        kafkaConsumerConfig = ConfigFactory.create(KafkaConsumerConfig.class, filterConfigs);

        filter = new JsonFilter(kafkaConsumerConfig, instrumentation);

        Message message = new Message(testKeyProto.toByteArray(), testMessageProto.toByteArray(), "topic1", 0, 100);
        List<Message> filteredMessages = this.filter.filter(Arrays.asList(message));
        assertEquals(filteredMessages.get(0), message);
    }

    @Test
    public void shouldLogFilterTypeIfFilterTypeIsNotNone() {
        Map<String, String> filterConfigs = new HashMap<>();
        filterConfigs.put("FILTER_JSON_DATA_SOURCE", "message");
        filterConfigs.put("FILTER_JSON_SCHEMA", "{\"properties\":{\"order_number\":{\"const\":123}}}");
        filterConfigs.put("FILTER_JSON_SCHEMA_PROTO_CLASS", TestMessage.class.getName());
        kafkaConsumerConfig = ConfigFactory.create(KafkaConsumerConfig.class, filterConfigs);

        new JsonFilter(kafkaConsumerConfig, instrumentation);
        Mockito.verify(instrumentation, Mockito.times(1)).logInfo("\n\tFilter type: {}", FilterDataSourceType.MESSAGE);
        Mockito.verify(instrumentation, Mockito.times(1)).logInfo("\n\tFilter schema: {}", TestMessage.class.getName());
        Mockito.verify(instrumentation, Mockito.times(1)).logInfo("\n\tFilter expression: {}", "{\"properties\":{\"order_number\":{\"const\":123}}}");
    }


    @Test
    public void shouldFilterEsbMessagesJson() throws FilterException {
        Message message = new Message(testKeyJson.getBytes(Charset.defaultCharset()), testMessageJson.getBytes(Charset.defaultCharset()), "topic1", 0, 100);
        filter = new JsonFilter(kafkaConsumerConfig, instrumentation);
        List<Message> filteredMessages = filter.filter(Arrays.asList(message));
        assertEquals(filteredMessages.get(0), message);
    }

    @Test
    public void shouldNotFilterEsbMessagesForEmptyBooleanValuesJson() throws FilterException {
        String bookingLogMessageJson = "{\"customer_id\":\"customerid\"}";
        String bookingLogKeyJson = "";
        Message message = new Message(bookingLogKeyJson.getBytes(Charset.defaultCharset()), bookingLogMessageJson.getBytes(Charset.defaultCharset()), "topic1", 0, 100);
        HashMap<String, String> bookingFilterConfigs = new HashMap<>();

        bookingFilterConfigs.put("FILTER_JSON_DATA_SOURCE", "message");
        bookingFilterConfigs.put("FILTER_JSON_SCHEMA", "{\"properties\":{\"customer_dynamic_surge_enabled\":{\"const\":\"true\"}}}");
        bookingFilterConfigs.put("FILTER_JSON_SCHEMA_PROTO_CLASS", TestBookingLogMessage.class.getName());

        KafkaConsumerConfig bookingConsumerConfig = ConfigFactory.create(KafkaConsumerConfig.class, bookingFilterConfigs);
        JsonFilter bookingFilter = new JsonFilter(bookingConsumerConfig, instrumentation);
        List<Message> filteredMessages = bookingFilter.filter(Arrays.asList(message));
        assertEquals(filteredMessages.get(0), message);
    }

    @Test(expected = FilterException.class)
    public void shouldThrowExceptionOnInvalidFilterExpressionJson() throws FilterException {
        Map<String, String> filterConfigs = new HashMap<>();
        filterConfigs.put("FILTER_JSON_DATA_SOURCE", "message");
        filterConfigs.put("FILTER_JSON_SCHEMA", "12/s");
        filterConfigs.put("FILTER_JSON_SCHEMA_PROTO_CLASS", TestMessage.class.getName());
        kafkaConsumerConfig = ConfigFactory.create(KafkaConsumerConfig.class, filterConfigs);

        filter = new JsonFilter(kafkaConsumerConfig, instrumentation);

        Message message = new Message(testKeyJson.getBytes(Charset.defaultCharset()), testMessageJson.getBytes(Charset.defaultCharset()), "topic1", 0, 100);
        filter.filter(Arrays.asList(message));
    }

    @Test
    public void shouldNotApplyFilterOnEmptyFilterTypeJson() throws FilterException {
        Map<String, String> filterConfigs = new HashMap<>();
        filterConfigs.put("FILTER_JSON_SCHEMA", "{\"properties\":{\"order_number\":{\"const\":\"1232\"}}}");
        filterConfigs.put("FILTER_ESB_MESSAGE_TYPE", "JSON");
        filterConfigs.put("FILTER_JSON_DATA_SOURCE", "KEY");

        kafkaConsumerConfig = ConfigFactory.create(KafkaConsumerConfig.class, filterConfigs);

        filter = new JsonFilter(kafkaConsumerConfig, instrumentation);

        Message message = new Message(testKeyJson.getBytes(Charset.defaultCharset()), testMessageJson.getBytes(Charset.defaultCharset()), "topic1", 0, 100);
        List<Message> filteredMessages = this.filter.filter(Arrays.asList(message));
        assertEquals(filteredMessages.get(0), message);
    }


    @Test
    public void shouldLogFilterTypeIfFilterTypeIsNone() {
        Map<String, String> filterConfigs = new HashMap<>();
        filterConfigs.put("FILTER_JSON_DATA_SOURCE", "none");
        kafkaConsumerConfig = ConfigFactory.create(KafkaConsumerConfig.class, filterConfigs);

        new JsonFilter(kafkaConsumerConfig, instrumentation);
        Mockito.verify(instrumentation, Mockito.times(1)).logInfo("No filter is selected");
    }
}

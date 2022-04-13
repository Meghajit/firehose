package io.odpf.firehose.filter.jexl;

import io.odpf.firehose.config.FilterConfig;
import io.odpf.firehose.config.enums.FilterDataSourceType;
import io.odpf.firehose.consumer.TestBookingLogKey;
import io.odpf.firehose.consumer.TestBookingLogMessage;
import io.odpf.firehose.consumer.TestKey;
import io.odpf.firehose.consumer.TestMessage;
import io.odpf.firehose.consumer.TestNestedRepeatedMessage;
import io.odpf.firehose.filter.Filter;
import io.odpf.firehose.filter.FilterException;
import io.odpf.firehose.filter.FilteredMessages;
import io.odpf.firehose.message.Message;
import io.odpf.firehose.metrics.Instrumentation;
import org.aeonbits.owner.ConfigFactory;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.Assert.assertEquals;

public class JexlFilterTest {
    private FilterConfig kafkaConsumerConfig;
    private Filter filter;
    private TestMessage testMessage;
    private TestKey key;
    private TestNestedRepeatedMessage repeatedTestMessage;
    private TestKey keyForNestedRepeatedMessage;

    @Mock
    private Instrumentation instrumentation;

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);
        Map<String, String> filterConfigs = new HashMap<>();
        filterConfigs.put("FILTER_DATA_SOURCE", "message");
        filterConfigs.put("FILTER_JEXL_EXPRESSION", "testMessage.getOrderNumber() == 123");
        filterConfigs.put("FILTER_SCHEMA_PROTO_CLASS", TestMessage.class.getName());
        kafkaConsumerConfig = ConfigFactory.create(FilterConfig.class, filterConfigs);

        key = TestKey.newBuilder().setOrderNumber("123").setOrderUrl("abc").build();
        testMessage = TestMessage.newBuilder().setOrderNumber("123").setOrderUrl("abc").setOrderDetails("details").build();

        TestMessage message1 = TestMessage.newBuilder().setOrderNumber("123").setOrderUrl("abc").setOrderDetails("details").build();
        TestMessage message2 = TestMessage.newBuilder().setOrderNumber("986").setOrderUrl("xyz").setOrderDetails("nothing").build();
        keyForNestedRepeatedMessage = TestKey.newBuilder().setOrderNumber("374245623434").setOrderUrl("hsfgsfgs").build();
        List<TestMessage> messageList = Stream.of(message1, message2).collect(Collectors.toList());
        repeatedTestMessage = TestNestedRepeatedMessage.newBuilder()
                .addAllRepeatedMessage(messageList)
                .setNumberField(7)
                .build();
    }

    @Test
    public void shouldFilterEsbMessages() throws FilterException {
        Message message = new Message(key.toByteArray(), this.testMessage.toByteArray(), "topic1", 0, 100);
        filter = new JexlFilter(kafkaConsumerConfig, instrumentation);
        FilteredMessages filteredMessages = filter.filter(Arrays.asList(message));
        FilteredMessages expectedMessages = new FilteredMessages();
        expectedMessages.addToValidMessages(message);
        assertEquals(expectedMessages, filteredMessages);
    }

    @Test
    public void shouldFilterEsbMessagesBasedOnRepeatedField() throws FilterException {
        Message message = new Message(keyForNestedRepeatedMessage.toByteArray(), repeatedTestMessage.toByteArray(), "topic1", 0, 100);
        String expression = "{for (message : testNestedRepeatedMessage.getRepeatedMessageList()) {if (message.getOrderNumber() == 986) { return true; } } return false; }";

        Map<String, String> filterConfigs = new HashMap<>();
        filterConfigs.put("FILTER_DATA_SOURCE", "message");
        filterConfigs.put("FILTER_JEXL_EXPRESSION", expression);
        filterConfigs.put("FILTER_SCHEMA_PROTO_CLASS", TestNestedRepeatedMessage.class.getName());
        kafkaConsumerConfig = ConfigFactory.create(FilterConfig.class, filterConfigs);

        filter = new JexlFilter(kafkaConsumerConfig, instrumentation);
        FilteredMessages filteredMessages = filter.filter(Arrays.asList(message));
        FilteredMessages expectedMessages = new FilteredMessages();
        expectedMessages.addToValidMessages(message);
        assertEquals(expectedMessages, filteredMessages);
    }

    @Test
    public void shouldNotFilterEsbMessagesForEmptyBooleanValues() throws FilterException {
        TestBookingLogMessage bookingLogMessage = TestBookingLogMessage.newBuilder().setCustomerId("customerId").build();
        TestBookingLogKey bookingLogKey = TestBookingLogKey.newBuilder().build();
        Message message = new Message(bookingLogKey.toByteArray(), bookingLogMessage.toByteArray(), "topic1", 0, 100);
        HashMap<String, String> bookingFilterConfigs = new HashMap<>();
        bookingFilterConfigs.put("FILTER_DATA_SOURCE", "message");
        bookingFilterConfigs.put("FILTER_JEXL_EXPRESSION", "testBookingLogMessage.getCustomerDynamicSurgeEnabled() == false");
        bookingFilterConfigs.put("FILTER_SCHEMA_PROTO_CLASS", TestBookingLogMessage.class.getName());
        FilterConfig bookingConsumerConfig = ConfigFactory.create(FilterConfig.class, bookingFilterConfigs);
        JexlFilter bookingFilter = new JexlFilter(bookingConsumerConfig, instrumentation);
        FilteredMessages expectedMessages = new FilteredMessages();
        expectedMessages.addToValidMessages(message);
        FilteredMessages filteredMessages = bookingFilter.filter(Arrays.asList(message));
        assertEquals(expectedMessages, filteredMessages);
    }

    @Test(expected = FilterException.class)
    public void shouldThrowExceptionOnInvalidFilterExpression() throws FilterException {
        Map<String, String> filterConfigs = new HashMap<>();
        filterConfigs.put("FILTER_DATA_SOURCE", "message");
        filterConfigs.put("FILTER_JEXL_EXPRESSION", "1+2");
        filterConfigs.put("FILTER_SCHEMA_PROTO_CLASS", TestMessage.class.getName());
        kafkaConsumerConfig = ConfigFactory.create(FilterConfig.class, filterConfigs);

        filter = new JexlFilter(kafkaConsumerConfig, instrumentation);
        key = TestKey.newBuilder().setOrderNumber("123").setOrderUrl("abc").build();
        this.testMessage = TestMessage.newBuilder().setOrderNumber("123").setOrderUrl("abc").setOrderDetails("details").build();

        Message message = new Message(key.toByteArray(), this.testMessage.toByteArray(), "topic1", 0, 100);
        filter.filter(Arrays.asList(message));
    }

    @Test
    public void shouldLogFilterTypeIfFilterTypeIsNotNone() {
        Map<String, String> filterConfigs = new HashMap<>();
        filterConfigs.put("FILTER_DATA_SOURCE", "message");
        filterConfigs.put("FILTER_JEXL_EXPRESSION", "testMessage.getOrderNumber() == 123");
        filterConfigs.put("FILTER_SCHEMA_PROTO_CLASS", TestMessage.class.getName());
        kafkaConsumerConfig = ConfigFactory.create(FilterConfig.class, filterConfigs);

        new JexlFilter(kafkaConsumerConfig, instrumentation);
        Mockito.verify(instrumentation, Mockito.times(1)).logInfo("\n\tFilter type: {}", FilterDataSourceType.MESSAGE);
        Mockito.verify(instrumentation, Mockito.times(1)).logInfo("\n\tFilter schema: {}", TestMessage.class.getName());
        Mockito.verify(instrumentation, Mockito.times(1)).logInfo("\n\tFilter expression: {}", "testMessage.getOrderNumber() == 123");
    }
}

package bf.rnc.common.events.rabbit;

/**
 * Queues RabbitMQ pour les tâches asynchrones (notifications, recouvrement, batch).
 */
public final class RabbitQueues {

    private RabbitQueues() {}

    public static final String SMS_NOTIFICATIONS = "rnc.notifications.sms";
    public static final String EMAIL_NOTIFICATIONS = "rnc.notifications.email";
    public static final String PUSH_NOTIFICATIONS = "rnc.notifications.push";

    public static final String COLLECTION_REMINDERS = "rnc.collect.reminders";
    public static final String COLLECTION_ESCALATION = "rnc.collect.escalation";

    public static final String REPORT_GENERATION = "rnc.reports.generation";
    public static final String STATEMENT_GENERATION = "rnc.statements.generation";
}

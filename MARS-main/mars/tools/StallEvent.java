package mars.tools;

public class StallEvent {
    public final int producerIndex;
    public final int consumerIndex;
    public final int stallCycles;
    public final String hazardType;
    public final String producerText;
    public final String consumerText;

    public StallEvent(int producerIndex, int consumerIndex, int stallCycles,
                      String hazardType, String producerText,
                      String consumerText) {
        this.producerIndex = producerIndex;
        this.consumerIndex = consumerIndex;
        this.stallCycles = stallCycles;
        this.hazardType = hazardType;
        this.producerText = producerText;
        this.consumerText = consumerText;
    }
}

package dev.dealcart.gateway.dto;

import dev.dealcart.v1.PriceQuote;

/**
 * DTO for JSON serialization of price quotes.
 */
public class PriceQuoteDto {
    private String vendor;
    private String vendorId;
    private double price;
    private String currency;
    private int estimatedDays;
    private long timestamp;
    
    public PriceQuoteDto() {}
    
    public static PriceQuoteDto fromProto(PriceQuote proto) {
        PriceQuoteDto dto = new PriceQuoteDto();
        dto.vendor = proto.getVendorName();
        dto.vendorId = proto.getVendorId();
        dto.price = proto.getPrice().getAmountCents() / 100.0;
        dto.currency = proto.getPrice().getCurrencyCode();
        dto.estimatedDays = proto.getEstimatedDays();
        dto.timestamp = proto.getTimestampMs();
        return dto;
    }
    
    // Getters and setters
    public String getVendor() { return vendor; }
    public void setVendor(String vendor) { this.vendor = vendor; }
    
    public String getVendorId() { return vendorId; }
    public void setVendorId(String vendorId) { this.vendorId = vendorId; }
    
    public double getPrice() { return price; }
    public void setPrice(double price) { this.price = price; }
    
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    
    public int getEstimatedDays() { return estimatedDays; }
    public void setEstimatedDays(int estimatedDays) { this.estimatedDays = estimatedDays; }
    
    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
}


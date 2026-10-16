package com.teamb.scheduler.search;

import com.teamb.scheduler.model.Appointment;
import java.time.*;
import java.util.*;

public class SearchRanking {
    public List<Map<String, Object>> findFreeSlots(
            String provider, LocalDate date, int duration, List<Appointment> bookings) {

        LocalDateTime dayStart = date.atTime(9, 0);
        LocalDateTime dayEnd = date.atTime(17, 0);
        List<Map<String, Object>> result = new ArrayList<>();

        LocalDateTime cursor = dayStart;
        for (Appointment booking : bookings) {
            if (booking.start().isAfter(cursor)) {
                addSlotIfLongEnough(result, cursor, booking.start(), duration);
            }
            if (booking.end().isAfter(cursor)) cursor = booking.end();
        }
        addSlotIfLongEnough(result, cursor, dayEnd, duration);

        result.sort(Comparator
                .comparing((Map<String,Object> m) -> (String)m.get("start"))
                .thenComparing(m -> (String)m.get("end")));

        return result;
    }

    private void addSlotIfLongEnough(List<Map<String,Object>> result,
                                      LocalDateTime start, LocalDateTime end, int duration) {
        if (Duration.between(start, end).toMinutes() >= duration) {
            LocalDateTime slotEnd = start.plusMinutes(duration);
            Map<String,Object> slot = new LinkedHashMap<>();
            slot.put("start", start.toString());
            slot.put("end", slotEnd.toString());
            result.add(slot);
        }
    }
}

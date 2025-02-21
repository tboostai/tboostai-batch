package com.tboostai_batch.util;

import java.util.regex.Pattern;
public class CleanDataUtils {

    public static String cleanBodyType(String raw) {
        if (raw == null || raw.trim().isEmpty() || raw.trim().equals("--")) {
            return null; // For empty strings or placeholder values, return null
        }
        String normalized = raw.trim().toLowerCase();

        // Check for SUV related terms
        if (Pattern.compile("\\bsuv\\b", Pattern.CASE_INSENSITIVE).matcher(normalized).find() ||
                normalized.contains("sport utility vehicle")) {
            return "SUV";
        }
        // Check for Sedan: if it contains "sedan", equals "2d", or contains "fastback"
        else if (Pattern.compile("\\bsedan\\b", Pattern.CASE_INSENSITIVE).matcher(normalized).find() ||
                normalized.equals("2d") ||
                normalized.contains("fastback")) {
            return "Sedan";
        }
        // Check for Hatchback
        else if (Pattern.compile("\\bhatchback\\b", Pattern.CASE_INSENSITIVE).matcher(normalized).find()) {
            return "Hatchback";
        }
        // Check for Pickup types: matches if it contains "pickup" (e.g., "pickup (truck)", "extended cab pickup", "crew cab pickup")
        else if (normalized.contains("pickup") || normalized.contains("truck")) {
            return "Truck";
        }
        // Check for Coupe type
        else if (normalized.contains("coupe")) {
            return "Coupe";
        }
        // Check for Convertible type
        else if (normalized.contains("convertible")) {
            return "Convertible";
        }
        // Check for Wagon type
        else if (normalized.contains("wagon")) {
            return "Wagon";
        }
        // Check for Minivan type: if it contains "minivan", "passenger van", or "standard passenger van"
        else if (normalized.contains("minivan") || normalized.contains("passenger van") ||
                normalized.contains("standard passenger van")) {
            return "Minivan";
        }
        // Check for Crossover type if present
        else if (normalized.contains("crossover")) {
            return "Crossover";
        }
        // Explicitly check for "van camper" and classify as "Other"
        else if (normalized.contains("van camper")) {
            return "Other";
        }
        // If none of the conditions match, classify as "Other"
        return "Other";
    }

    public static String cleanEngineType(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return null;
        }
        String normalized = raw.trim().toLowerCase();

        // If it contains "flex fuel", classify as "Gasoline"
        if (normalized.contains("flex fuel")) {
            return "Gasoline";
        }
        // If it contains "diesel", classify as "Diesel"
        else if (normalized.contains("diesel")) {
            return "Diesel";
        }
        // If it contains "electric", then check for hybrid details
        else if (normalized.contains("electric")) {
            if (normalized.contains("hybrid") && normalized.contains("plug")) {
                return "Plug-In Hybrid";
            } else if (normalized.contains("hybrid")) {
                return "Hybrid";
            }
            return "Electric";
        }
        // If it contains "petrol", "gasoline", or simply "gas", classify as "Gasoline"
        else if (normalized.contains("petrol") || normalized.contains("gasoline") || normalized.contains("gas")) {
            return "Gasoline";
        }
        // If it only contains "hybrid" (and did not match the electric condition above), classify as "Hybrid"
        else if (normalized.contains("hybrid")) {
            return "Hybrid";
        }
        // Default classification as "Gasoline"
        return "Other";
    }
}

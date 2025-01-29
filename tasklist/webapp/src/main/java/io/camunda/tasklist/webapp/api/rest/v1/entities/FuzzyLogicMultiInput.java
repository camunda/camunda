import java.util.*;

class FuzzyLogicMultiInput {
  // DueDate Membership Functions (Triangular & Trapezoidal)
  private static double pertinenciaCurto(final double x) {
    if (x >= 0 && x <= 2) {
      return 1;
    }
    if (x > 2 && x <= 5) {
      return (5 - x) / 3;
    }
    return 0;
  }

  private static double pertinenciaMedio(final double x) {
    if (x >= 2 && x <= 5) {
      return (x - 2) / 3;
    }
    if (x > 5 && x <= 8) {
      return (8 - x) / 3;
    }
    return 0;
  }

  private static double pertinenciaLongo(final double x) {
    if (x >= 5 && x <= 8) {
      return (x - 5) / 3;
    }
    if (x > 8) {
      return 1;
    }
    return 0;
  }

  // Priority Membership Functions (Trapezoidal)
  private static double pertinenciaBaixa(final double x) {
    if (x <= 20) {
      return 1;
    }
    if (x > 20 && x <= 50) {
      return (50 - x) / 30;
    }
    return 0;
  }

  private static double pertinenciaMedia(final double x) {
    if (x >= 30 && x <= 50) {
      return (x - 30) / 20;
    }
    if (x > 50 && x <= 70) {
      return (70 - x) / 20;
    }
    return 0;
  }

  private static double pertinenciaAlta(final double x) {
    if (x >= 80) {
      return 1;
    }
    if (x > 60 && x < 80) {
      return (x - 60) / 20;
    }
    return 0;
  }

  // Fuzzy Rules & Classification
  private static String regraFuzzy(final double dueDate, final double priority) {
    final double curto = pertinenciaCurto(dueDate);
    final double medio = pertinenciaMedio(dueDate);
    final double longo = pertinenciaLongo(dueDate);

    final double baixa = pertinenciaBaixa(priority);
    final double media = pertinenciaMedia(priority);
    final double alta = pertinenciaAlta(priority);

    // Print pertinences
    System.out.println("Curto: " + curto);
    System.out.println("Médio: " + medio);
    System.out.println("Longo: " + longo);
    System.out.println("Baixa: " + baixa);
    System.out.println("Média: " + media);
    System.out.println("Alta: " + alta);

    // Defuzzification Calculation
    final double saidaCrisp = calcularSaidaCrisp(curto, medio, longo, baixa, media, alta);
    System.out.println("Saída Crisp: " + saidaCrisp);

    // Output Classification Based on MATLAB-defined Labels
    if (saidaCrisp >= 8) {
      return "Urgent";
    } else if (saidaCrisp >= 6) {
      return "High";
    } else if (saidaCrisp >= 4) {
      return "Medium";
    } else if (saidaCrisp >= 2) {
      return "Low";
    } else {
      return "Very_Low";
    }
  }

  // Defuzzification Using Weighted Average (Centroid)
  private static double calcularSaidaCrisp(final double curto, final double medio, final double longo,
      final double baixa, final double media, final double alta) {
    // Adjusted Centroid Positions Based on MATLAB Graph
    final double xVeryLow = 1;   // Center of [0,2,4]
    final double xLow = 3;       // Center of [2,4,6]
    final double xMedium = 5;    // Center of [4,6,8]
    final double xHigh = 7;      // Center of [6,8,10]
    final double xUrgent = 9;    // Center of [8,10,12]

    // Maximum Membership for Each Output Label
    final double wVeryLow = Math.max(longo, baixa);
    final double wLow = Math.max(medio, baixa);
    final double wMedium = Math.max(medio, media);
    final double wHigh = Math.max(curto, media);
    final double wUrgent = Math.max(curto, alta);

    // Weighted Sum
    final double num = (wVeryLow * xVeryLow) +
        (wLow * xLow) +
        (wMedium * xMedium) +
        (wHigh * xHigh) +
        (wUrgent * xUrgent);

    // Sum of Weights
    final double den = wVeryLow + wLow + wMedium + wHigh + wUrgent;

    if (den == 0) {
      return 0; // Avoid Division by Zero
    }

    return num / den;
  }

  public static void main(final String[] args) {
    // Test cases for validation
    double dueDate = 3;
    double priority = 80;
    System.out.println("Classificação da tarefa: " + regraFuzzy(dueDate, priority));

    dueDate = 5;
    priority = 80;
    System.out.println("Classificação da tarefa: " + regraFuzzy(dueDate, priority));

    dueDate = 9;
    priority = 80;
    System.out.println("Classificação da tarefa: " + regraFuzzy(dueDate, priority));
  }
}

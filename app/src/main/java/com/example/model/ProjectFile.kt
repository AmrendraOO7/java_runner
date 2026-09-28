package com.example.model

import java.util.UUID

data class ProjectFile(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val content: String,
    val uriString: String? = null,
    val lastModified: Long = System.currentTimeMillis(),
    val isModified: Boolean = false
) {
    companion object {
        fun defaultMain(): ProjectFile = ProjectFile(
            id = "default_main",
            name = "Main.java",
            content = """
                import java.util.*;

                public class Main {
                    public static void main(String[] args) {
                        System.out.println("====================================");
                        System.out.println("  Welcome to Java Runner on Android ");
                        System.out.println("====================================");
                        
                        String message = "Java Code Executed Successfully!";
                        System.out.println("Status: " + message);
                        
                        // Current environment info
                        System.out.println("Java Version: " + System.getProperty("java.version", "11"));
                        System.out.println("OS: " + System.getProperty("os.name", "Android"));
                        
                        // Array and loop demo
                        int[] numbers = {10, 4, 32, 9, 21};
                        Arrays.sort(numbers);
                        System.out.print("Sorted numbers: ");
                        for (int n : numbers) {
                            System.out.print(n + " ");
                        }
                        System.out.println();
                    }
                }
            """.trimIndent()
        )

        fun sampleTemplates(): List<ProjectFile> = listOf(
            defaultMain(),
            ProjectFile(
                id = "template_calculator",
                name = "Calculator.java",
                content = """
                    import java.util.Scanner;

                    public class Calculator {
                        public static void main(String[] args) {
                            Scanner scanner = new Scanner(System.in);
                            System.out.println("--- Interactive Math Calculator ---");
                            System.out.println("Enter first number:");
                            
                            double num1 = scanner.hasNextDouble() ? scanner.nextDouble() : 25.0;
                            System.out.println("First Number: " + num1);
                            
                            System.out.println("Enter operator (+, -, *, /):");
                            String op = scanner.hasNext() ? scanner.next() : "+";
                            System.out.println("Operator: " + op);
                            
                            System.out.println("Enter second number:");
                            double num2 = scanner.hasNextDouble() ? scanner.nextDouble() : 15.0;
                            System.out.println("Second Number: " + num2);
                            
                            double result = 0;
                            if (op.equals("+")) {
                                result = num1 + num2;
                            } else if (op.equals("-")) {
                                result = num1 - num2;
                            } else if (op.equals("*")) {
                                result = num1 * num2;
                            } else if (op.equals("/")) {
                                result = num2 != 0 ? num1 / num2 : 0;
                            }
                            
                            System.out.println("Result: " + num1 + " " + op + " " + num2 + " = " + result);
                        }
                    }
                """.trimIndent()
            ),
            ProjectFile(
                id = "template_datastructures",
                name = "DataStructures.java",
                content = """
                    import java.util.*;

                    public class DataStructures {
                        public static void main(String[] args) {
                            System.out.println("--- Collections & Maps Demo ---");
                            
                            // List of items
                            List<String> fruits = new ArrayList<String>();
                            fruits.add("Apple");
                            fruits.add("Banana");
                            fruits.add("Cherry");
                            fruits.add("Dragonfruit");
                            
                            System.out.println("Fruit count: " + fruits.size());
                            for (String fruit : fruits) {
                                System.out.println(" - " + fruit);
                            }
                            
                            // Map of Scores
                            Map<String, Integer> studentGrades = new HashMap<String, Integer>();
                            studentGrades.put("Alice", 95);
                            studentGrades.put("Bob", 88);
                            studentGrades.put("Charlie", 92);
                            
                            System.out.println("\nStudent Grades:");
                            for (String student : studentGrades.keySet()) {
                                System.out.println(" > " + student + ": " + studentGrades.get(student));
                            }
                        }
                    }
                """.trimIndent()
            ),
            ProjectFile(
                id = "template_fibonacci",
                name = "Fibonacci.java",
                content = """
                    public class Fibonacci {
                        public static long fib(int n) {
                            if (n <= 1) return n;
                            return fib(n - 1) + fib(n - 2);
                        }

                        public static void main(String[] args) {
                            int count = 12;
                            System.out.println("Computing first " + count + " Fibonacci numbers:");
                            
                            long start = System.currentTimeMillis();
                            for (int i = 0; i < count; i++) {
                                System.out.println("F(" + i + ") = " + fib(i));
                            }
                            long elapsed = System.currentTimeMillis() - start;
                            System.out.println("Computation took: " + elapsed + " ms");
                        }
                    }
                """.trimIndent()
            ),
            ProjectFile(
                id = "template_bank",
                name = "BankAccount.java",
                content = """
                    public class BankAccount {
                        private String accountHolder;
                        private double balance;

                        public BankAccount(String holder, double initialBalance) {
                            this.accountHolder = holder;
                            this.balance = initialBalance;
                        }

                        public void deposit(double amount) {
                            if (amount > 0) {
                                balance += amount;
                                System.out.println("Deposited $" + amount + ". New balance: $" + balance);
                            }
                        }

                        public void withdraw(double amount) {
                            if (amount > 0 && amount <= balance) {
                                balance -= amount;
                                System.out.println("Withdrew $" + amount + ". Remaining balance: $" + balance);
                            } else {
                                System.out.println("Withdrawal of $" + amount + " declined (insufficient funds)");
                            }
                        }

                        public void printSummary() {
                            System.out.println("Account: " + accountHolder + " | Balance: $" + balance);
                        }

                        public static void main(String[] args) {
                            BankAccount account = new BankAccount("Alex Jordan", 500.0);
                            account.printSummary();
                            account.deposit(150.0);
                            account.withdraw(70.0);
                            account.withdraw(700.0);
                            account.printSummary();
                        }
                    }
                """.trimIndent()
            ),
            ProjectFile(
                id = "template_primes",
                name = "PrimeSieve.java",
                content = """
                    import java.util.Arrays;

                    public class PrimeSieve {
                        public static void main(String[] args) {
                            int limit = 50;
                            boolean[] isPrime = new boolean[limit + 1];
                            Arrays.fill(isPrime, true);
                            isPrime[0] = false;
                            isPrime[1] = false;

                            for (int p = 2; p * p <= limit; p++) {
                                if (isPrime[p]) {
                                    for (int i = p * p; i <= limit; i += p) {
                                        isPrime[i] = false;
                                    }
                                }
                            }

                            System.out.println("Primes up to " + limit + ":");
                            int count = 0;
                            for (int i = 2; i <= limit; i++) {
                                if (isPrime[i]) {
                                    System.out.print(i + " ");
                                    count++;
                                }
                            }
                            System.out.println("\nTotal primes found: " + count);
                        }
                    }
                """.trimIndent()
            ),
            ProjectFile(
                id = "template_Address",
                name = "AddressTests.java",
                content = """
                import java.net.*;

                    public class AddressTests {

                        public static int getVersion(InetAddress ia) {
                            byte[] address = ia.getAddress();

                            if (address.length == 4)
                                return 4;
                            else if (address.length == 16)
                                return 6;
                            else
                                return -1;
                        }

                        public static void main(String[] args) {
                            try {
                                InetAddress address = InetAddress.getByName("www.google.com");

                                int version = getVersion(address);

                                System.out.println("IP Address: " +
                                        address.getHostAddress());

                                if (version == 4)
                                    System.out.println("IP Version: IPv4");
                                else if (version == 6)
                                    System.out.println("IP Version: IPv6");
                                else
                                    System.out.println("IP Version: Unknown");

                            } catch (UnknownHostException e) {
                                System.out.println("Could not find the host.");
                            }
                        }
                    }
                """.trimIndent()
            )
        )
    }
}

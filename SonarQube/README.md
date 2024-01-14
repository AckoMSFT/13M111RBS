Use the following command to generate the SonarQube report:
- `java -jar sonar-cnes-report-4.2.0.jar -p com.zuehlke:secure-software-development`

```
PS D:\git\sonar-cnes-report> java -jar sonar-cnes-report-4.2.0.jar -p com.zuehlke:secure-software-development
SonarQube URL: http://localhost:9000
SonarQube online: true
Detected SonarQube version: 8.9.1.44547
Report generation: SUCCESS
```

The interesting data can be found in the `SonarQubeReport.xlsx` worksheet under the `Security Hotspots` sheet.
The matching screenshots are located in the `Screenshots` directory. 
All of the false positive vulnerabilities have FP in the suffix of their screenshot.
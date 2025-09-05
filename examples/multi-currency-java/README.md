1. **Start Vespa container**:
   ```bash
   vespa config set target local
   docker pull vespaengine/vespa
   docker run --detach --name vespa --hostname vespa-container \
     --publish 127.0.0.1:8080:8080 --publish 127.0.0.1:19071:19071 \
     vespaengine/vespa
   ```

2. **Build**
    ```bash
    mvn clean package -U
    ```

3. **Wait for Vespa to be ready**
   ```bash
   vespa status deploy --wait 300
   ```

4. **Deploy the application**:
   ```bash
   vespa deploy --wait 300
   ```

5. **Feed**
```bash
vespa feed items.jsonl
```
```bash
python3 currency_xml_to_vespa_docs.py | vespa feed -
```

6. **Test**
```bash
mvn test
```

7. **Example Query**
```bash
vespa query 'select * from item where true' \
 'min-price=20' \
 'max-price=80' \
 'currency=usd' \
 'searchChain=multic'
```

8. **Notes**
- Currently hardcoded fetch methods in the searcher


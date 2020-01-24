(ns com.caioaao.tank.try-run)

(defn try-run
  "Runs `proc` and returns a tuple denoting the result.
  The first element on the tuple is a keyword denoting the result as follows:
  - If there's an exception `catch?` is called with the exception as argument.
  If `catch?`returns true, return `::exception`. Otherwise it will rethrow the
  exception.
  - If no exceptions are thrown, `failed?` is called with the result. If
  `failed?` returns true, return `::failed`
  - If no exceptions are thrown, `failed?` is called with the result. If
  `failed?` returns false, return `::succeeded`"
  [proc & {:keys [catch? failed?]
           :or {catch?  (constantly false)
                failed? (constantly false)}}]
  (try
    (as-> (proc) $
      [(if (failed? $) ::failed ::succeeded) $])
    (catch Exception ex
      (if (catch? ex)
        [::exception ex]
        (throw ex)))))

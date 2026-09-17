package body Sample is

   function Create (Name : String) return Object is
      Result : Object;
   begin
      Result.Length := Natural'Min (Name'Length, Max_Name_Length);
      Result.Name (1 .. Result.Length) :=
        Name (Name'First .. Name'First + Result.Length - 1);
      return Result;
   end Create;

   function Name (Item : Object) return String is
   begin
      return Item.Name (1 .. Item.Length);
   end Name;

end Sample;
